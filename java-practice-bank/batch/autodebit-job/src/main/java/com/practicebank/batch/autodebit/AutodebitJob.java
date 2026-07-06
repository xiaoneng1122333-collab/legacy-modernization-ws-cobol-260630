package com.practicebank.batch.autodebit;

import com.practicebank.common.batch.BatchJobConfig;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Boot エントリポイント — AD-RUN-DAILY / AD-REPORT-SUMMARY.
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code runDaily} — 自動引き落とし指令を 1 件ずつ POST (AD-RUN-DAILY)</li>
 *   <li>{@code reportSummary} — 集計レポート出力 (AD-REPORT-SUMMARY)</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class AutodebitJob {
    public static void main(String[] args) {
        SpringApplication.run(AutodebitJob.class, args);
    }

    @Bean
    public Job autodebitRunJob(JobRepository jobRepository,
                               PlatformTransactionManager txManager,
                               Step runDaily,
                               Step reportSummary) {
        return new JobBuilder("autodebitJob", jobRepository)
            .start(runDaily)
            .next(reportSummary)
            .listener(new AutodebitSummaryListener())
            .build();
    }
}
