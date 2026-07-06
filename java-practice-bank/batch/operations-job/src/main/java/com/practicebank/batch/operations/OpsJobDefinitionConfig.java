package com.practicebank.batch.operations;

import com.practicebank.batch.operations.monthly.OpsMonthlyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * OPS-BATCH-DAILY 相当.
 * 日次パイプラインの Step を定義順にまとめる (19 → 13 → 15 → 16 → 17 → 20).
 * 各ステップ定義本体は各 Config で実装され, ここでは順序を保証する Job を定義する.
 */
@Configuration
public class OpsJobDefinitionConfig {

    private static final Logger LOG = LoggerFactory.getLogger(OpsJobDefinitionConfig.class);

    /**
     * 日次本番用 Job (OPS-BATCH-DAILY).
     *
     * <p>Step 実行順序:</p>
     * <pre>
     *   batchRunStart
     *     → masterLoadCalendar → masterLoadBranch → masterLoadCustomer
     *     → masterLoadProduct  → masterLoadInterestRate → masterLoadFeeSchedule
     *     → masterLoadAccount
     *     → seedSystemAccounts
     *     → step19Inti → step13Iacr → step15Ad → step16Fee → step17Stmt → step20Drain
     *     → opsFinalize
     *     → scanDormancy (週次相当. smoke 同着)
     *     → batchRunComplete
     * </pre>
     */
    @Bean
    @Primary
    public Job opsBatchDailyJob(JobRepository jr, OpsJobListener listener,
                               Step batchRunStart,
                               Step masterLoadCalendar, Step masterLoadBranch, Step masterLoadCustomer,
                               Step masterLoadProduct, Step masterLoadInterestRate,
                               Step masterLoadFeeSchedule, Step masterLoadAccount,
                               Step seedSystemAccounts,
                               Step step19Inti, Step step13Iacr, Step step15Ad, Step step16Fee,
                               Step step17Stmt, Step step20Drain,
                               Step opsFinalize,
                               Step scanDormancy,
                               Step batchRunComplete) {
        return new JobBuilder("opsBatchDailyJob", jr)
            .start(batchRunStart)
            .next(masterLoadCalendar)
            .next(masterLoadBranch)
            .next(masterLoadCustomer)
            .next(masterLoadProduct)
            .next(masterLoadInterestRate)
            .next(masterLoadFeeSchedule)
            .next(masterLoadAccount)
            .next(seedSystemAccounts)
            .next(step19Inti)
            .next(step13Iacr)
            .next(step15Ad)
            .next(step16Fee)
            .next(step17Stmt)
            .next(step20Drain)
            .next(opsFinalize)
            .next(scanDormancy)
            .next(batchRunComplete)
            .listener(listener)
            .build();
    }

    /** 月次パイプライン Job (OPS-BATCH-MONTHLY). */
    @Bean
    public Job opsBatchMonthlyJob(JobRepository jr, OpsMonthlyListener listener,
                                 Step batchRunStart,
                                 Step step14Ipst,
                                 Step partitionRollover,
                                 Step batchRunComplete) {
        return new JobBuilder("opsBatchMonthlyJob", jr)
            .start(batchRunStart)
            .next(step14Ipst)
            .next(partitionRollover)
            .next(batchRunComplete)
            .listener(listener)
            .build();
    }
}
