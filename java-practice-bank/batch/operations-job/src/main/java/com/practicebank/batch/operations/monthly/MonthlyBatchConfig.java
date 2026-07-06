package com.practicebank.batch.operations.monthly;

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
import org.springframework.transaction.PlatformTransactionManager;

/**
 * OPS-BATCH-MONTHLY 相当 — 月次パイプライン (14-IPST → PARTITION-ROLLOVER).
 *
 * <p>本バッチのステップ:</p>
 * <ol>
 *   <li>batch-run-start          (共通 BatchRunStepsConfig)</li>
 *   <li>ops-step-14-ipst         (14-interestpost IPST-RUN-MONTHEND)</li>
 *   <li>ops-partition-rollover   (21-audit AUDIT-PARTITION-ROLLOVER)</li>
 *   <li>batch-run-complete       (共通 BatchRunStepsConfig)</li>
 * </ol>
 */
@Configuration
public class MonthlyBatchConfig {

    private static final Logger LOG = LoggerFactory.getLogger(MonthlyBatchConfig.class);

    @Bean
    public Step step14Ipst(JobRepository jr, PlatformTransactionManager tx, OpsAudit audit,
                           @Value("${ops.batch-id:}") String batchId,
                           @Value("${ops.business-date:}") String businessDate,
                           @Value("${ops.dry-run:N}") String dryRun) {
        return new StepBuilder("step14Ipst", jr)
            .tasklet(new Tasklet() {
                @Override
                public RepeatStatus execute(StepContribution sc, ChunkContext cc) {
                    audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_START, cc);
                    if ("Y".equalsIgnoreCase(dryRun)) {
                        LOG.info("OPS-STEP-14-IPST dry-run smoke batch={}", batchId);
                    } else {
                        LOG.info("OPS-STEP-14-IPST submodule=IPST-RUN-MONTHEND (smoke — IPST connector TBA) batch={}", batchId);
                    }
                    audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_OK, cc);
                    LOG.info("OPS-STEP-14-IPST-OK batch={}", batchId);
                    return RepeatStatus.FINISHED;
                }
            }, tx)
            .build();
    }

    @Bean
    public Step partitionRollover(JobRepository jr, PlatformTransactionManager tx, OpsAudit audit,
                                  @Value("${ops.batch-id:}") String batchId,
                                  @Value("${ops.business-date:}") String businessDate,
                                  @Value("${ops.dry-run:N}") String dryRun) {
        return new StepBuilder("partitionRollover", jr)
            .tasklet(new Tasklet() {
                @Override
                public RepeatStatus execute(StepContribution sc, ChunkContext cc) {
                    audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_START, cc);
                    LOG.info("OPS-PARTITION-ROLLOVER-START batch={} dryRun={} (smoke — APR connector TBA)",
                        batchId, dryRun);
                    // "WS-OPR-STATUS" 相当 (常に 00 を返却. Phase 2 T).
                    audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_OK, cc);
                    LOG.info("OPS-PARTITION-ROLLOVER-OK batch={}", batchId);
                    return RepeatStatus.FINISHED;
                }
            }, tx)
            .build();
    }
}
