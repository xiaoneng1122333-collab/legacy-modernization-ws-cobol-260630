package com.practicebank.batch.txnpost;

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
 * Spring Boot エントリポイント — TXPOST-RUN-BATCH / TXPOST-REVERSE / TXPOST-REPORT-SUMMARY.
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code txpostRunBatch} — transactions テーブルの PT 取引をカーソル走査し dual-entry 記帳 (TXPOST-RUN-BATCH)</li>
 *   <li>{@code txpostReverse} — 逆伝票 Step (TXPOST-REVERSE: 指定 orig-txn-id を逆伝票)</li>
 *   <li>{@code txpostReportSummary} — transactions / postings / balances から集計を取得しレポート出力 (TXPOST-REPORT-SUMMARY)</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class TxpostRunBatchJob {
    public static void main(String[] args) {
        SpringApplication.run(TxpostRunBatchJob.class, args);
    }

    @Bean
    public Job txpostJob(JobRepository jobRepository,
                          Step txpostRunBatch,
                          Step txpostReverse,
                          Step txpostReportSummary) {
        return new JobBuilder("txpostRunBatchJob", jobRepository)
            .start(txpostRunBatch)
            .next(txpostReverse)
            .next(txpostReportSummary)
            .listener(new TxpostSummaryListener())
            .build();
    }
}
