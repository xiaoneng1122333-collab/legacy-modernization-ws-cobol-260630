package com.practicebank.batch.integrationout;

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
 * Spring Boot エントリポイント — INTO-DRAIN-QUEUE / INTO-PUBLISH-EVENT.
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code drainQueue} — autodebit 失敗キューファイルを読み込みイベント発行 (INTO-DRAIN-QUEUE)</li>
 *   <li>{@code publishEvent} — JSON エンベロープ生成 + MQ publish (INTO-PUBLISH-EVENT)</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class IntegrationOutJob {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationOutJob.class, args);
    }

    @Bean
    public Job integrationOutPipeline(JobRepository jobRepository,
                                       Step drainQueue,
                                       Step publishEvent) {
        return new JobBuilder("integrationOutJob", jobRepository)
            .start(drainQueue)
            .next(publishEvent)
            .build();
    }
}
