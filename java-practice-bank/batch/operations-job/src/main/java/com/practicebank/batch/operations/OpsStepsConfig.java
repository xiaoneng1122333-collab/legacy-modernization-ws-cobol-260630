package com.practicebank.batch.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * OPS-BATCH-DAILY の 6 ステップ (19-INTI .. 20-DRAIN) を定義する.
 *
 * <p>Phase 2 では各ステップの本体は他サブシステム (.so バイナリ) が実行する想定を残し,
 * Tasklet は smoke/marker 実装 (監査 + ログ出力) にとどめる.
 * ops-step-*.sh の DRY-RUN=Y 相当は tasklet 内で処理する.</p>
 *
 * <p>各ステップ ID は ops-batch-daily.md / ops-step-wrappers.md と一致:</p>
 * <pre>
 *   19-INTI = INTI-DECODE-BATCH           (19-integrationin)
 *   13-IACR = IACR-RUN-DAILY              (13-interestaccrual)
 *   15-AD   = AD-RUN-DAILY                (15-autodebit)
 * †† 16-FEE  = FEE-CHARGE                 (16-fee)
 *   17-STMT = STMT-GENERATE-BATCH         (17-statement)
 *   20-DRAIN = INTO-DRAIN-QUEUE           (20-integrationout)
 * </pre>
 */
@Configuration
public class OpsStepsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(OpsStepsConfig.class);

    /** パイプライン順序に従ったステップ定義. (順序 = 日次定義). */
    public record StepDef(String stepName, String moduleId, String description) {}

    /** 全ステップ定義一覧 (順次実行順序). */
    public static final List<StepDef> DAILY_STEPS = List.of(
        new StepDef("step19Inti",  "19-INTI",  "INTI-DECODE-BATCH"),
        new StepDef("step13Iacr", "13-IACR", "IACR-RUN-DAILY"),
        new StepDef("step15Ad",   "15-AD",   "AD-RUN-DAILY"),
        new StepDef("step16Fee",  "16-FEE",  "FEE-CHARGE"),
        new StepDef("step17Stmt", "17-STMT", "STMT-GENERATE-BATCH"),
        new StepDef("step20Drain","20-DRAIN","INTO-DRAIN-QUEUE")
    );

    /**
     * パイプライン各ステップの Tasklet ファクトリ.
     * DRY_RUN=Y 相当: smoke ログのみ本番モジュール接続と同じフォーマットで出力する.
     */
    private Tasklet opsStepTasklet(OpsAudit audit,
                                   String batchId,
                                   String businessDate,
                                   String dryRun,
                                   StepDef def) {
        final boolean isDry = "Y".equalsIgnoreCase(dryRun);
        return (StepContribution contribution, ChunkContext chunkContext) -> {
            audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_START, chunkContext);
            LOG.info("OPS-STEP-START step={} module={} [{}]", def.stepName(), def.moduleId(), def.description());
            if (isDry) {
                LOG.info("OPS-STEP-DRYRUN step={} skipped submodule execution", def.stepName());
            } else {
                // TODO: Phase 2 で各サブシステムの .so / REST をここで実行.
                // 例: cobcrun ops-step-19-inti.sh. 本 Phase 2 では smoke 出力のみ.
                LOG.info("OPS-STEP-EXEC step={} submodule={} (smoke — real connector TBA)",
                    def.stepName(), def.moduleId());
            }
            audit.writeStepFromChunk(batchId, businessDate, OpsAudit.EVT_STEP_OK, chunkContext);
            LOG.info("OPS-STEP-OK step={}", def.stepName());
            return RepeatStatus.FINISHED;
        };
    }

    private Step buildOpsStep(String name, StepDef def, JobRepository jr, PlatformTransactionManager tx,
                              OpsAudit audit, String batchId, String businessDate, String dryRun) {
        return new StepBuilder(name, jr)
            .tasklet(opsStepTasklet(audit, batchId, businessDate, dryRun, def), tx)
            .build();
    }

    @Bean
    public Step step19Inti(JobRepository jr, PlatformTransactionManager tx, OpsAudit audit,
                           @Value("${ops.batch-id:}") String batchId,
                           @Value("${ops.business-date:}") String businessDate,
                           @Value("${ops.dry-run:N}") String dryRun) {
        return buildOpsStep("step19Inti", DAILY_STEPS.get(0), jr, tx, audit, batchId, businessDate, dryRun);
    }

    @Bean
    public Step step13Iacr(JobRepository jr, PlatformTransactionManager tx, OpsAudit audit,
                           @Value("${ops.batch-id:}") String batchId,
                           @Value("${ops.business-date:}") String businessDate,
                           @Value("${ops.dry-run:N}") String dryRun) {
        return buildOpsStep("step13Iacr", DAILY_STEPS.get(1), jr, tx, audit, batchId, businessDate, dryRun);
    }

    @Bean
    public Step step15Ad(JobRepository jr, PlatformTransactionManager tx, OpsAudit audit,
                         @Value("${ops.batch-id:}") String batchId,
                         @Value("${ops.business-date:}") String businessDate,
                         @Value("${ops.dry-run:N}") String dryRun) {
        return buildOpsStep("step15Ad", DAILY_STEPS.get(2), jr, tx, audit, batchId, businessDate, dryRun);
    }

    @Bean
    public Step step16Fee(JobRepository jr, PlatformTransactionManager tx, OpsAudit audit,
                          @Value("${ops.batch-id:}") String batchId,
                          @Value("${ops.business-date:}") String businessDate,
                          @Value("${ops.dry-run:N}") String dryRun) {
        return buildOpsStep("step16Fee", DAILY_STEPS.get(3), jr, tx, audit, batchId, businessDate, dryRun);
    }

    @Bean
    public Step step17Stmt(JobRepository jr, PlatformTransactionManager tx, OpsAudit audit,
                           @Value("${ops.batch-id:}") String batchId,
                           @Value("${ops.business-date:}") String businessDate,
                           @Value("${ops.dry-run:N}") String dryRun) {
        return buildOpsStep("step17Stmt", DAILY_STEPS.get(4), jr, tx, audit, batchId, businessDate, dryRun);
    }

    @Bean
    public Step step20Drain(JobRepository jr, PlatformTransactionManager tx, OpsAudit audit,
                            @Value("${ops.batch-id:}") String batchId,
                            @Value("${ops.business-date:}") String businessDate,
                            @Value("${ops.dry-run:N}") String dryRun) {
        return buildOpsStep("step20Drain", DAILY_STEPS.get(5), jr, tx, audit, batchId, businessDate, dryRun);
    }
}
