package com.practicebank.batch.txnsortmerge;

import com.practicebank.common.batch.BatchJobConfig;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot エントリポイント — TXSM-SORT-BATCH / TXSM-MERGE-BATCH / TXSM-REPORT-SUMMARY.
 *
 * <p>Step 構成:</p>
 * <ol>
 *   <li>{@code sort} — 妥当性検証済み (status=VO) の取引を payer-acct → seq 昇順でソート (TXSM-SORT-BATCH)</li>
 *   <li>{@code merge} — ソート済みストリームと前日取引 (txn_recon_prev) を 2-way マージ (TXSM-MERGE-BATCH)</li>
 *   <li>{@code reportSummary} — 集計結果をレポート出力 (TXSM-REPORT-SUMMARY)</li>
 * </ol>
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class TxsmSortMergeJob {
    public static void main(String[] args) {
        SpringApplication.run(TxsmSortMergeJob.class, args);
    }

    @Bean
    public Job txsmSortMergeJobDefinition(JobRepository jobRepository,
                                           @Qualifier("sortStepTasklet") Step sort,
                                           @Qualifier("mergeStep") Step merge,
                                           @Qualifier("reportSummary") Step reportSummary) {
        return new JobBuilder("txsmSortMergeJob", jobRepository)
            .start(sort)
            .next(merge)
            .next(reportSummary)
            .listener(new TxsmSummaryListener())
            .build();
    }
}
