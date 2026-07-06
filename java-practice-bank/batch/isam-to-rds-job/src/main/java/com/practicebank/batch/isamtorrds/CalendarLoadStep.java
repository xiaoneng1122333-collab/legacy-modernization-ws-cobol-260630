// java-practice-bank/batch/isam-to-rds-job/src/main/java/com/practicebank/batch/isamtorrds/CalendarLoadStep.java
package com.practicebank.batch.isamtorrds;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * Step テンプレート: カレンダマスタ (ISAM → shared.calendar)。
 * 他の Step (branch / customer / product / interestrate / feeschedule / account)
 * も同様のパターンで実装する。
 */
@Configuration
public class CalendarLoadStep {

    @Bean
    public Step loadCalendar(JobRepository jobRepository,
                             PlatformTransactionManager txManager,
                             @Value("${isam.base-path}") String basePath) {
        return new StepBuilder("loadCalendar", jobRepository)
            .<IsamRecord, IsamRecord>chunk(1000, txManager)
            .reader(new IsamFileReader(
                Path.of(basePath, "01-calendar/data/calendar.idx"),
                64, // TODO: レコード長を正確に (COBOL FD に基づく)
                new String[]{"cal_date", "day_type", "holiday_name"}
            ))
            .processor(record -> record) // 変換ロジックは後で実装
            .writer(calendarWriter())
            .build();
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<IsamRecord> calendarWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<IsamRecord>()
            .dataSource(dataSource)
            .sql("INSERT INTO shared.calendar (cal_date, day_type, holiday_name) " +
                  "VALUES (:fields.get('cal_date'), :fields.get('day_type'), :fields.get('holiday_name'))")
            .beanMapped()
            .build();
    }
}
