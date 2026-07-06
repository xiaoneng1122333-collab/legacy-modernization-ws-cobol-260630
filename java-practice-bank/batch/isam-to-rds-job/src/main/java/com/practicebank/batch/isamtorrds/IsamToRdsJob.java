// java-practice-bank/batch/isam-to-rds-job/src/main/java/com/practicebank/batch/isamtorrds/IsamToRdsJob.java
package com.practicebank.batch.isamtorrds;

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
 * Spring Batch ジョブ: ISAM 7 ファイルを読み取り、Aurora (PostgreSQL) に書き込む。
 * 各 Step はマスタ単位 (calendar / branch / customer / product /
 * interestrate / feeschedule / account) に分割。
 */
@SpringBootApplication
@Import(BatchJobConfig.class)
public class IsamToRdsJob {

    public static void main(String[] args) {
        SpringApplication.run(IsamToRdsJob.class, args);
    }

    @Bean
    public Job isamToRdsJob(JobRepository jobRepository,
                            Step loadCalendar,
                            Step loadBranch,
                            Step loadCustomer,
                            Step loadProduct,
                            Step loadInterestRate,
                            Step loadFeeSchedule,
                            Step loadAccount) {
        return new JobBuilder("isamToRdsJob", jobRepository)
            .start(loadCalendar)
            .next(loadBranch)
            .next(loadCustomer)
            .next(loadProduct)
            .next(loadInterestRate)
            .next(loadFeeSchedule)
            .next(loadAccount)
            .build();
    }

    // 各 Step は Task 9 で CalendarLoadStep などを参照実装として作成
}
