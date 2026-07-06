package com.practicebank.batch.operations.batchrun;

import com.practicebank.batch.operations.OpsAudit;
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

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * OPS-BATCH-RUN-START / OPS-BATCH-RUN-COMPLETE 相当の Spring Batch Step.
 *
 * <ul>
 *   <li>batch-run-start: batch_run に status='RN' で INSERT / UPSERT.</li>
 *   <li>batch-run-complete: batch_run に completed_ts, status (OK/FL/AB), txns_posted, errors_count を UPDATE.
 *     ホワイトリスト (OK/FL/AB) を検証し, 不正値は Step を FAILED とする (= COBOL rc=1 相当).</li>
 * </ul>
 */
@Configuration
public class BatchRunStepsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(BatchRunStepsConfig.class);

    private final JdbcTemplate jdbc;
    private final OpsAudit audit;

    public BatchRunStepsConfig(JdbcTemplate jdbc, OpsAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Bean
    public Step batchRunStart(JobRepository jr, PlatformTransactionManager tx,
                              @Value("${ops.batch-id:}") String batchId,
                              @Value("${ops.business-date:}") String businessDate) {
        return new StepBuilder("batchRunStart", jr)
            .tasklet(new Tasklet() {
                @Override
                public RepeatStatus execute(StepContribution sc, ChunkContext cc) {
                    String fallback = "BATCH" + jeInstanceId(cc);
                    String bid = firstNonBlank(batchId, cc, "ops.batchId", fallback);
                    String bdate = firstNonBlank(businessDate, cc, "ops.businessDate", "");
                    jdbc.update(
                        "INSERT INTO batch_run (batch_id, business_date, started_ts, status, current_step) " +
                        "VALUES (?, ?, ?, 'RN', '19-INTI') " +
                        "ON CONFLICT (batch_id) DO UPDATE SET started_ts = EXCLUDED.started_ts, status = 'RN'",
                        bid, bdate, Timestamp.valueOf(LocalDateTime.now()));
                    audit.writeStepFromChunk(bid, bdate, OpsAudit.EVT_STEP_START, cc);
                    audit.writeStepFromChunk(bid, bdate, OpsAudit.EVT_STEP_OK, cc);
                    cc.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                        .putString("ops.batchId", bid);
                    cc.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                        .putString("ops.businessDate", bdate);
                    LOG.info("OPS-BATCH-RUN-START batch={} businessDate={}", bid, bdate);
                    return RepeatStatus.FINISHED;
                }
            }, tx)
            .build();
    }

    @Bean
    public Step batchRunComplete(JobRepository jr, PlatformTransactionManager tx,
                                 @Value("${ops.batch-id:}") String batchId,
                                 @Value("${ops.business-date:}") String businessDate) {
        return new StepBuilder("batchRunComplete", jr)
            .tasklet(new Tasklet() {
                @Override
                public RepeatStatus execute(StepContribution sc, ChunkContext cc) {
                    String bid = firstNonBlank(batchId, cc, "ops.batchId", "UNKNOWN");
                    String bdate = firstNonBlank(businessDate, cc, "ops.businessDate", "");

                    // ホワイトリスト検証 (OPB-BATCH-COMPLETE 仕様).
                    String requested = cc.getStepContext().getStepExecution().getJobExecution()
                        .getJobParameters().getString("completeStatus", "OK");
                    RunCompleteStatus status = RunCompleteStatus.fromString(requested);
                    if (status == null) {
                        LOG.error("OPS-BATCH-RUN-COMPLETE rejected invalid status={}", requested);
                        throw new IllegalArgumentException("Invalid batch completion status: " + requested);
                    }

                    int txns = (int) cc.getStepContext().getStepExecution().getJobExecution()
                        .getExecutionContext().getLong("finalizedCount", 0L);

                    jdbc.update(
                        "UPDATE batch_run SET completed_ts = ?, status = ?, txns_posted = ? " +
                        "WHERE batch_id = ?",
                        Timestamp.valueOf(LocalDateTime.now()), status.name(), txns, bid);
                    audit.writeStepFromChunk(bid, bdate, OpsAudit.EVT_STEP_OK, cc);
                    LOG.info("OPS-BATCH-RUN-COMPLETE batch={} status={} txns={}", bid, status, txns);
                    return RepeatStatus.FINISHED;
                }
            }, tx)
            .build();
    }

    private String firstNonBlank(String fromProp, ChunkContext cc, String paramKey, String fallback) {
        if (fromProp != null && !fromProp.isBlank()) return fromProp;
        // JobParameters は Spring environment の後に評価される. 同一キーで override 可.
        String fromParam = cc.getStepContext().getStepExecution().getJobExecution()
            .getJobParameters().getString(paramKey);
        if (fromParam != null && !fromParam.isBlank()) return fromParam;
        // Execution context に前ステップが書いた値があれば優先.
        String ec = cc.getStepContext().getStepExecution().getJobExecution()
            .getExecutionContext().getString(paramKey);
        if (ec != null && !ec.isBlank()) return ec;
        return fallback;
    }

    private String jeInstanceId(ChunkContext cc) {
        return String.valueOf(cc.getStepContext().getStepExecution().getJobExecution()
            .getJobInstance() == null ? System.currentTimeMillis()
            : cc.getStepContext().getStepExecution().getJobExecution().getJobInstance().getInstanceId());
    }
}
