package com.practicebank.batch.operations.finalize;

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

/**
 * OPS-FINALIZE 相当 — PT (posted) ステータスのトランザクションを一括で SE (settled) に更新する後処理.
 *
 * <p>OPF-CHUNK-SIZE は将来拡張. 現実装は単一 UPDATE で更新し, 更新件数を JobExecutionContext に格納する.</p>
 */
@Configuration
public class FinalizeConfig {

    private static final Logger LOG = LoggerFactory.getLogger(FinalizeConfig.class);

    private final JdbcTemplate jdbc;
    private final OpsAudit audit;

    public FinalizeConfig(JdbcTemplate jdbc, OpsAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Bean
    public Step opsFinalize(JobRepository jr, PlatformTransactionManager tx,
                            @Value("${ops.batch-id:}") String batchId,
                            @Value("${ops.business-date:}") String businessDate) {
        return new StepBuilder("opsFinalize", jr)
            .tasklet(new Tasklet() {
                @Override
                public RepeatStatus execute(StepContribution sc, ChunkContext cc) {
                    String bid = resolve(batchId, cc, "ops.batchId", "UNKNOWN");
                    String bdate = resolve(businessDate, cc, "ops.businessDate", "");
                    audit.writeStepFromChunk(bid, bdate, OpsAudit.EVT_STEP_START, cc);

                    String status = cc.getStepContext().getStepExecution().getJobExecution()
                        .getJobParameters().getString("inputStatus", "PT");
                    long updated = jdbc.update(
                        "UPDATE transactions SET status = 'SE' " +
                        "WHERE source_batch_id = ? AND business_date = ?::date AND status = ?",
                        bid, bdate, status);
                    cc.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                        .putLong("finalizedCount", updated);

                    audit.writeStepFromChunk(bid, bdate, OpsAudit.EVT_STEP_OK, cc);
                    LOG.info("OPS-FINALIZE updated={} batch={} businessDate={}", updated, bid, bdate);
                    return RepeatStatus.FINISHED;
                }
            }, tx)
            .build();
    }

    private String resolve(String fromProp, ChunkContext cc, String paramKey, String fallback) {
        if (fromProp != null && !fromProp.isBlank()) return fromProp;
        String fromParam = cc.getStepContext().getStepExecution().getJobExecution()
            .getJobParameters().getString(paramKey);
        if (fromParam != null && !fromParam.isBlank()) return fromParam;
        return fallback;
    }
}
