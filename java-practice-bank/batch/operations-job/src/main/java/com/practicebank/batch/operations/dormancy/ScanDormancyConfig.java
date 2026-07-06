package com.practicebank.batch.operations.dormancy;

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
 * OPS-SCAN-DORMANCY 相当 — 週次の休眠/再活性スキャンを顺次実行する.
 *
 * <p>休眠 → 再活性の 2 段階構成. Phase 2 では smoke 実装.
 * 再活性の rc はブロッキングしない (WARN) 仕様を反映.</p>
 */
@Configuration
public class ScanDormancyConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ScanDormancyConfig.class);

    @Bean
    public Step scanDormancy(JobRepository jr, PlatformTransactionManager tx, OpsAudit audit,
                             @Value("${ops.batch-id:}") String batchId,
                             @Value("${ops.business-date:}") String businessDate,
                             @Value("${ops.dry-run:N}") String dryRun) {
        return new StepBuilder("scanDormancy", jr)
            .tasklet(new Tasklet() {
                @Override
                public RepeatStatus execute(StepContribution sc, ChunkContext cc) {
                    audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_START, cc);
                    LOG.info("OPS-SCAN-DORMANCY-START batch={} businessDate={} dryRun={}",
                        batchId, businessDate, dryRun);

                    // 休眠スキャン (ALC-DORMANCY-SCAN).
                    int dormRc = "Y".equalsIgnoreCase(dryRun) ? 0 : smoke("ALC-DORMANCY-SCAN");
                    if (dormRc == 0) {
                        // 休眠成功時のみ再活性スキャン (非ブロッキング WARN).
                        int reactRc = "Y".equalsIgnoreCase(dryRun) ? 0 : smoke("ALC-REACTIVATION-SCAN");
                        if (reactRc != 0) {
                            LOG.warn("OPS-REACTIVATION-SCAN rc={} (non-blocking WARN)", reactRc);
                        }
                    } else if (dormRc >= 8 && dormRc <= 12) {
                        LOG.info("OPS-DORMANCY-SOFT-SKIP rc={} (treated as success)", dormRc);
                    } else {
                        audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_FAIL, cc);
                        throw new RuntimeException("ALC-DORMANCY-SCAN failed rc=" + dormRc);
                    }
                    audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_OK, cc);
                    LOG.info("OPS-SCAN-DORMANCY-OK batch={}", batchId);
                    return RepeatStatus.FINISHED;
                }

                /** 0 = smoke 成功. 将来は cobcrun ALC-DORMANCY-SCAN.so を実行. */
                private int smoke(String moduleName) {
                    LOG.info("OPS-SCAN-DORMANCY smoke submodule={}", moduleName);
                    return 0;
                }
            }, tx)
            .build();
    }
}
