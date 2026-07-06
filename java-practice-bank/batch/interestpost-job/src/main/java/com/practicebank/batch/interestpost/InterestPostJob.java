package com.practicebank.batch.interestpost;

import com.practicebank.batch.interestpost.config.InterestPostJobListener;
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
 * Spring Boot エントリポイント — IPST-RUN-MONTHEND / IPST-REPORT-SUMMARY.
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code runMonthend} — 月次利息仕訳生成・記帳 (IPST-RUN-MONTHEND)</li>
 *   <li>{@code reportSummary} — 保存量検証・サマリ出力 (IPST-REPORT-SUMMARY)</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class InterestPostJob {
    public static void main(String[] args) {
        SpringApplication.run(InterestPostJob.class, args);
    }

    @Bean
    public Job interestpostRunMonthend(JobRepository jobRepository,
                                       Step runMonthend,
                                       Step reportSummary) {
        return new JobBuilder("interestpostRunMonthend", jobRepository)
            .start(runMonthend)
            .next(reportSummary)
            .listener(new InterestPostJobListener())
            .build();
    }
}
