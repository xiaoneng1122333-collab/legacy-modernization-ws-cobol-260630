package com.practicebank.batch.integrationout.config;

import com.practicebank.batch.integrationout.domain.EventEnvelope;
import com.practicebank.batch.integrationout.domain.PublishEventInput;
import com.practicebank.batch.integrationout.domain.PublishEventOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.UUID;

/**
 * INTO-PUBLISH-EVENT 相当の Spring Batch Step.
 *
 * <p>イベント種別に応じた JSON ペイロードを生成し, envelope を組み立てて MQ ブローカーへ publish する.
 * 最大 3 回のリトライを行い, 成否に応じた監査ログを出力する.
 * Phase 2 では MQ publish 相当をログ出力に置き換える.</p>
 */
@Configuration
public class IntoPublishEventConfig {

    private static final Logger LOG = LoggerFactory.getLogger(IntoPublishEventConfig.class);

    private static final int MAX_RETRIES = 3;

    @Value("${integrationout.publish.event-type:#{null}}")
    private String eventType;

    @Value("${integrationout.publish.business-date:#{null}}")
    private String businessDateStr;

    @Value("${integrationout.publish.batch-id:#{null}}")
    private String batchId;

    @Value("${integrationout.publish.txn-id:#{null}}")
    private String txnId;

    @Value("${integrationout.publish.account:#{null}}")
    private String account;

    @Value("${integrationout.publish.amount-jpy:#{null}}")
    private Long amountJpy;

    @Value("${integrationout.publish.category:#{null}}")
    private String category;

    @Value("${integrationout.publish.reason:#{null}}")
    private String reason;

    @Value("${integrationout.publish.count:0}")
    private int count;

    @Value("${integrationout.publish.mode:M}")
    private String mode;

    @Value("${integrationout.mock.broker:false}")
    private boolean mockBroker;

    @Bean
    public Step publishEvent(JobRepository jobRepository,
                             PlatformTransactionManager txManager,
                             Tasklet publishEventTasklet) {
        return new StepBuilder("publishEvent", jobRepository)
            .tasklet(publishEventTasklet, txManager)
            .build();
    }

    @Bean
    public Tasklet publishEventTasklet() {
        return new Tasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
                PublishEventInput input = buildInput();

                // EMIT-AUDIT-START
                LOG.info("EMIT-AUDIT-START: eventType={} mode={}", eventType, mode);

                // VALIDATE-INPUT
                if (!input.isValid()) {
                    LOG.warn("INTO-PUBLISH-EVENT: INVALID-INPUT (eventType={})", eventType);
                    PublishEventOutput output = PublishEventOutput.invalidInput();
                    writeOutput(chunkContext, output);
                    return RepeatStatus.FINISHED;
                }

                // UUID 生成 (eventId)
                String eventId = UUID.randomUUID().toString();

                // タイムスタンプ生成 + ペイロード組み立て + エンベロープ組み立て
                String payload = EventEnvelope.buildPayload(input);
                EventEnvelope envelope = EventEnvelope.of(eventId, input, payload);

                LOG.info("ENVELOPE: version={} eventId={} eventType={} businessDate={} publishedAt={} source={}",
                    envelope.version(), envelope.eventId(), envelope.eventType(),
                    envelope.businessDate(), envelope.publishedAt(), envelope.source());

                // PUBLISH with retry (max 3)
                int retries = 0;
                boolean success = false;
                for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                    retries = attempt;
                    success = publishOnce(envelope, attempt);
                    if (success) break;
                    LOG.warn("PUBLISH-ONCE failed attempt={} eventId={}", attempt, eventId);
                }

                PublishEventOutput output;
                if (success) {
                    output = PublishEventOutput.ok(eventId, retries);
                } else {
                    output = PublishEventOutput.retryExhausted(retries);
                }

                // EMIT-AUDIT-END
                LOG.info("EMIT-AUDIT-END: eventId={} retries={} status={}", eventId, retries, output.status());
                writeOutput(chunkContext, output);

                return RepeatStatus.FINISHED;
            }
        };
    }

    private PublishEventInput buildInput() {
        LocalDate bdate = null;
        if (businessDateStr != null && !businessDateStr.isBlank()) {
            try {
                bdate = LocalDate.parse(businessDateStr);
            } catch (Exception e) {
                // invalid → isValid() で弾かれる
            }
        }
        return new PublishEventInput(eventType, bdate, batchId, txnId, account,
            amountJpy, category, reason, count, mode);
    }

    /** 1 回の publish 試行. Phase 2 ではモック/実 broker を問わずログ出力. */
    private boolean publishOnce(EventEnvelope envelope, int attempt) {
        // Phase 2 — simulated MQ publish. 本来は rmq_pub モジュールを呼出す.
        LOG.info("PUBLISH-ONCE attempt={} eventId={} eventType={} mode={}",
            attempt, envelope.eventId(), envelope.eventType(), mockBroker ? "MOCK" : "REAL");
        // モックモードでは常に成功. 実モードでも Phase 2 では成功扱い.
        return true;
    }

    private void writeOutput(ChunkContext chunkContext, PublishEventOutput output) {
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .put("into.publish.status", output.status());
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .put("into.publish.eventId", output.eventId() != null ? output.eventId() : "");
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .putInt("into.publish.retries", output.retryCount());
    }
}
