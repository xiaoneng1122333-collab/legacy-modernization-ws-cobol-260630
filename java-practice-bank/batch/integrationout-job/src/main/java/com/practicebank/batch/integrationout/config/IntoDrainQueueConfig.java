package com.practicebank.batch.integrationout.config;

import com.practicebank.batch.integrationout.domain.DrainQueueInput;
import com.practicebank.batch.integrationout.domain.DrainQueueOutput;
import com.practicebank.batch.integrationout.domain.FailedAutodebitRecord;
import com.practicebank.batch.integrationout.domain.PublishEventInput;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * INTO-DRAIN-QUEUE 相当の Spring Batch Step.
 *
 * <p>autodebit 失敗キューファイル (固定長 200 バイト逐次ファイル相当) を 1 レコードずつ読み取り,
 * FAILED イベントとして audit_log に出力する. publish 処理は INTO-PUBLISH-EVENT Step に委譲する.
 * Phase 2 では MQ publish 相当をログ出力に置き換える.</p>
 */
@Configuration
public class IntoDrainQueueConfig {

    private static final Logger LOG = LoggerFactory.getLogger(IntoDrainQueueConfig.class);

    @Value("${integrationout.drain.source-filename:#{null}}")
    private String sourceFilename;

    @Value("${integrationout.drain.max-records:10000}")
    private int maxRecords;

    @Value("${integrationout.drain.mode:M}")
    private String mode;

    @Value("${integrationout.business.date:#{null}}")
    private String businessDateStr;

    @Bean
    public Step drainQueue(JobRepository jobRepository,
                           PlatformTransactionManager txManager,
                           Tasklet drainQueueTasklet) {
        return new StepBuilder("drainQueue", jobRepository)
            .tasklet(drainQueueTasklet, txManager)
            .build();
    }

    @Bean
    public Tasklet drainQueueTasklet(org.springframework.jdbc.core.JdbcTemplate jdbc,
                                      @Value("${integrationout.mock.broker:false}") boolean mockBroker) {
        return new Tasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
                DrainQueueInput input = new DrainQueueInput(sourceFilename, maxRecords, mode);

                // EMIT-DRAIN-START-AUDIT (simulated via log)
                String runId = UUID.randomUUID().toString().substring(0, 8);
                LOG.info("EMIT-DRAIN-START-AUDIT: runId={} mode={} max={}", runId, mode, input.effectiveMaxRecords());

                // VALIDATE-INPUT
                if (!input.isValid()) {
                    LOG.warn("INTO-DRAIN-QUEUE: INVALID-INPUT (filename blank)");
                    DrainQueueOutput output = DrainQueueOutput.invalidInput();
                    writeOutput(chunkContext, output, 0);
                    return RepeatStatus.FINISHED;
                }

                // FAILED-FILE OPEN equivalent
                Path filePath = Paths.get(input.sourceFilename());
                List<FailedAutodebitRecord> records;
                try {
                    records = readRecords(filePath, input.effectiveMaxRecords());
                } catch (java.io.IOException e) {
                    LOG.warn("INTO-DRAIN-QUEUE: IO-FAIL opening {}: {}", sourceFilename, e.getMessage());
                    DrainQueueOutput output = DrainQueueOutput.ioFail();
                    writeOutput(chunkContext, output, 0);
                    return RepeatStatus.FINISHED;
                }

                // LOOP: 1 レコードずつ INTO-PUBLISH-EVENT 相当の処理
                int drained = 0;
                int failed = 0;
                for (FailedAutodebitRecord rec : records) {
                    boolean published = publishOne(rec, mockBroker);
                    if (published) {
                        drained++;
                    } else {
                        failed++;
                    }
                }

                DrainQueueOutput output;
                if (failed > 0 && drained > 0) {
                    output = DrainQueueOutput.partial(drained, failed);
                } else {
                    output = DrainQueueOutput.ok(drained);
                }

                LOG.info("EMIT-DRAIN-END-AUDIT: runId={} drained={} failed={}", runId, drained, failed);
                writeOutput(chunkContext, output, drained + failed);

                return RepeatStatus.FINISHED;
            }
        };
    }

    private List<FailedAutodebitRecord> readRecords(Path path, int max) throws java.io.IOException {
        if (!Files.exists(path)) {
            // FS=35 → EOF 扱い. 空リスト返却
            LOG.info("FAILED-FILE not found (treated as EOF): {}", path);
            return List.of();
        }
        var lines = Files.readAllLines(path);
        List<FailedAutodebitRecord> result = new ArrayList<>();
        for (int i = 0; i < Math.min(lines.size(), max); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            // CSV-like mock: txnId,account,amountJpy,reason
            String[] parts = line.split(",", 4);
            String txnId = parts.length > 0 ? parts[0] : "TXN" + i;
            String account = parts.length > 1 ? parts[1] : "0000000000000";
            long amount = parts.length > 2 ? parseOrZero(parts[2]) : 0L;
            String reason = parts.length > 3 ? parts[3] : "NF";
            result.add(new FailedAutodebitRecord(txnId, account, amount, reason));
        }
        return result;
    }

    private long parseOrZero(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** INTO-PUBLISH-EVENT 相当の処理: 1 レコードの publish (Phase 2 ではログ出力). */
    private boolean publishOne(FailedAutodebitRecord rec, boolean mockMode) {
        // Phase 2 — simulated MQ publish. 本来は rmq_pub モジュールを呼出す.
        LOG.info("INTO-PUBLISH-EVENT (drain): eventType=autodebit.failed account={} amountJpy={} reason={} mode={}",
            rec.account(), rec.amountJpy(), rec.reason(), mockMode ? "MOCK" : "REAL");
        // 失敗理由コードが "FATAL" の場合のみ publish 失敗相当
        return !"FATAL".equals(rec.reason());
    }

    private void writeOutput(ChunkContext chunkContext, DrainQueueOutput output, int total) {
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .putInt("into.drained", output.drained());
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .putInt("into.failed", output.failed());
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
            .put("into.status", output.status());
    }
}
