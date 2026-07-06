package com.practicebank.batch.statement;

import com.practicebank.batch.statement.config.StmtJobListener;
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
 * Spring Boot エントリポイント — STMT-GENERATE-BATCH.
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code stmtGenerate} — 4-level カーソル (customers → branches → accounts → transactions) で帳票生成.</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class StmtGenerateJob {
    public static void main(String[] args) {
        SpringApplication.run(StmtGenerateJob.class, args);
    }

    @Bean
    public Job stmtGenerateRun(JobRepository jobRepository,
                                Step stmtGenerate) {
        return new JobBuilder("stmtGenerateRun", jobRepository)
            .start(stmtGenerate)
            .listener(new StmtJobListener())
            .build();
    }
}
