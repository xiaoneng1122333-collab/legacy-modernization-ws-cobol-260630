package com.practicebank.batch.audit.config;

import com.practicebank.batch.audit.domain.PartitionRolloverInput;
import com.practicebank.batch.audit.domain.PartitionRolloverOutput;
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

/**
 * AUDIT-PARTITION-ROLLOVER 相当の Spring Batch Step (tasklet).
 *
 * <p>次月の audit_log パーティションを事前作成し, 保持期間を超過した古いパーティションを DETACH する.
 * Phase 2 ではパーティション操作相当をログ出力に置き換える (実際の DDL は本番デプロイ時に実施).</p>
 */
@Configuration
public class PartitionRolloverConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PartitionRolloverConfig.class);

    @Value("${audit.rollover.operator-user:#{null}}")
    private String operatorUser;

    @Value("${audit.rollover.retention-days:30}")
    private int retentionDays;

    @Value("${audit.rollover.dry-run:N}")
    private String dryRun;

    @Value("${audit.rollover.enable-detach:N}")
    private String enableDetach;

    @Value("${audit.business.date:#{null}}")
    private String businessDateStr;

    @Bean
    public Step partitionRollover(JobRepository jobRepository,
                                  PlatformTransactionManager txManager,
                                  Tasklet partitionRolloverTasklet) {
        return new StepBuilder("partitionRollover", jobRepository)
            .tasklet(partitionRolloverTasklet, txManager)
            .build();
    }

    @Bean
    public Tasklet partitionRolloverTasklet() {
        return new Tasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
                boolean isDryRun = "Y".equalsIgnoreCase(dryRun);
                boolean isDetachEnabled = "Y".equalsIgnoreCase(enableDetach);

                PartitionRolloverInput input = new PartitionRolloverInput(
                    operatorUser,
                    retentionDays,
                    isDryRun,
                    isDetachEnabled
                );

                // COMPUTE-NEXT: 次月初日/パーティション名算出
                LocalDate baseDate = businessDateStr != null && !businessDateStr.isBlank()
                    ? LocalDate.parse(businessDateStr)
                    : LocalDate.now();
                String nextPartition = PartitionRolloverOutput.computeNextPartitionName(baseDate);
                LocalDate horizon = PartitionRolloverOutput.computeHorizon(baseDate, input.effectiveRetentionDays());

                LOG.info("AUDIT-PARTITION-ROLLOVER start: operator={} dryRun={} detach={} next={} horizon={}",
                    input.effectiveOperator(), isDryRun, isDetachEnabled, nextPartition, horizon);

                // PARTITION_ROLL 出力 (simulated via log)
                LOG.info("AUDIT-WRITE PARTITION_ROLL: operator={} next={} created=1 horizon={}",
                    input.effectiveOperator(), nextPartition, horizon);

                int created = 0;
                int detached = 0;

                if (!isDryRun) {
                    // create_audit_partition 実行相当 → Phase 2 ではログ + フラグ更新のみ
                    LOG.info("SIM: CREATE PARTITION IF NOT EXISTS {}", nextPartition);
                    created = 1;

                    if (isDetachEnabled) {
                        // detach_expired_audit_partitions 実行相当
                        LOG.info("SIM: DETACH PARTITIONS BEFORE {}", horizon);
                        detached = 1;
                        // PART_DETACHED 出力 (simulated via log)
                        LOG.info("AUDIT-WRITE PART_DETACHED: operator={} detached={} horizon={}",
                            input.effectiveOperator(), detached, horizon);
                    }
                } else {
                    LOG.info("DRY-RUN: create/detach skipped. next={} horizon={}", nextPartition, horizon);
                }

                PartitionRolloverOutput output = PartitionRolloverOutput.ok(created, detached, nextPartition);

                // 結果を JobExecutionContext へ格納
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                    .put("apr.status", output.status());
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                    .putInt("apr.created", output.createdCount());
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                    .putInt("apr.detached", output.detachedCount());
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                    .put("apr.nextPartition", output.nextPartition());

                LOG.info("AUDIT-PARTITION-ROLLOVER end: status={} created={} detached={} next={}",
                    output.status(), output.createdCount(), output.detachedCount(), output.nextPartition());

                return RepeatStatus.FINISHED;
            }
        };
    }
}
