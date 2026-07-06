package com.practicebank.batch.interestaccrual;

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
 * Spring Boot エントリポイント — IACR-RUN-DAILY / IACR-REPORT-SUMMARY.
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code iacrRunDaily} — balances テーブルをカーソル走査し日次利息を算出 (IACR-RUN-DAILY)</li>
 *   <li>{@code iacrReportSummary} — interest_accruals から AC/PT/全体件数を取得しレポート出力 (IACR-REPORT-SUMMARY)</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class IacrRunDailyJob {
    public static void main(String[] args) {
        SpringApplication.run(IacrRunDailyJob.class, args);
    }

    @Bean
    public Job iacrBatchJob(JobRepository jobRepository,
                            Step iacrRunDaily,
                            Step iacrReportSummary) {
        return new JobBuilder("iacrRunDailyJob", jobRepository)
            .start(iacrRunDaily)
            .next(iacrReportSummary)
            .listener(new IacrSummaryListener())
            .build();
    }
}
