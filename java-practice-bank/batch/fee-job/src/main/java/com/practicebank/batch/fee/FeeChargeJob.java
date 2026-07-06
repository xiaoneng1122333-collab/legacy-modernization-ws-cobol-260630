package com.practicebank.batch.fee;

import com.practicebank.batch.fee.config.FeeChargeJobListener;
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
 * Spring Boot エントリポイント — FEE-CHARGE / FEE-REPORT-SUMMARY.
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code feeCharge} — 手数料仕訳生成・記帳 (FEE-CHARGE)</li>
 *   <li>{@code feeReportSummary} — 保存量検証・レポート出力 (FEE-REPORT-SUMMARY)</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class FeeChargeJob {
    public static void main(String[] args) {
        SpringApplication.run(FeeChargeJob.class, args);
    }

    @Bean
    public Job feeChargeRun(JobRepository jobRepository,
                             Step feeCharge,
                             Step feeReportSummary) {
        return new JobBuilder("feeChargeRun", jobRepository)
            .start(feeCharge)
            .next(feeReportSummary)
            .listener(new FeeChargeJobListener())
            .build();
    }
}
