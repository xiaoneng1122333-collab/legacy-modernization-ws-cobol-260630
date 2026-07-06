package com.practicebank.batch.integrationin;

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
 * Spring Boot エントリポイント — INTI-DECODE-BATCH (19-integrationin)。
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code decode} — 固定長レコード (H/D/T) を読み込み、デコード/バリデーション →
 *       妥当レコードを transactions テーブルに書き出す (Phase 2: 実 EBCDIC バイナリは読まず
 *       CSV ライクなテストフィクスチャを入力とする)</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class IntiDecodeJob {
    public static void main(String[] args) {
        SpringApplication.run(IntiDecodeJob.class, args);
    }

    @Bean
    public Job integrationInDecodeJob(JobRepository jobRepository, Step decode) {
        return new JobBuilder("integrationInDecodeJob", jobRepository)
            .start(decode)
            .listener(new IntiDecodeJobListener())
            .build();
    }
}
