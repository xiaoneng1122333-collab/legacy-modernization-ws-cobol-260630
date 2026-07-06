package com.practicebank.batch.txnvalidate;

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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Boot エントリポイント — TXVAL-VALIDATE-BATCH / TXVAL-CHECKPOINT-RECOVER / TXVAL-REPORT-SUMMARY.
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code recoverCheckpoint} — チェックポイント復元 (TXVAL-CHECKPOINT-RECOVER)</li>
 *   <li>{@code validate} — トランザクション明細バリデーション (TXVAL-VALIDATE-BATCH)</li>
 *   <li>Job 終了時に TXVAL-REPORT-SUMMARY が集計結果を出力</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class TxvalValidateJob {
    public static void main(String[] args) {
        SpringApplication.run(TxvalValidateJob.class, args);
    }

    @Bean
    public Job txnvalValidateJob(JobRepository jobRepository,
                                 PlatformTransactionManager txManager,
                                 Step recoverCheckpoint,
                                 Step validate) {
        return new JobBuilder("txnvalValidateJob", jobRepository)
            .start(recoverCheckpoint)
            .next(validate)
            .listener(new TransactionValidationSummaryListener())
            .build();
    }
}
