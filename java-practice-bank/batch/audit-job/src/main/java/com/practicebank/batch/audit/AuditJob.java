package com.practicebank.batch.audit;

import com.practicebank.common.batch.BatchJobConfig;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot エントリポイント — AUDIT-3-STEP-PIPELINE.
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code partitionRollover} — 次月パーティション事前作成 + 経過パーティション DETACH (AUDIT-PARTITION-ROLLOVER)</li>
 *   <li>{@code queryForensic} — 監査証跡検索 + ファイル出力 (AUDIT-QUERY-FORENSIC)</li>
 *   <li>{@code summaryReport} — 日付/サブシステム別集計レポート出力 (AUDIT-SUMMARY-REPORT)</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class AuditJob {
    public static void main(String[] args) {
        SpringApplication.run(AuditJob.class, args);
    }

    @Bean
    public Job auditPipeline(JobRepository jobRepository,
                              Step partitionRollover,
                              Step queryForensic,
                              Step summaryReport) {
        return new JobBuilder("auditJob", jobRepository)
            .start(partitionRollover)
            .next(queryForensic)
            .next(summaryReport)
            .build();
    }
}
