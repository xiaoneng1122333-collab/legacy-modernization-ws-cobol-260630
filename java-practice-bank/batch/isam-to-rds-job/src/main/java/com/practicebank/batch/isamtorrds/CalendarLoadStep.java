package com.practicebank.batch.isamtorrds;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Step テンプレート: カレンダマスタ (ISAM → shared.calendar)。
 * 他の Step (branch / customer / ...) も同様のパターンで作る。
 */
@Configuration
public class CalendarLoadStep {

    @Bean
    public Step loadCalendar(JobRepository jobRepository,
                             PlatformTransactionManager txManager,
                             JdbcBatchItemWriter<IsamRecord> calendarWriter,
                             @Value("${isam.base-path}") String basePath) {
        Path isamFile = Path.of(basePath, "01-calendar/data/calendar.idx");
        // Phase 1: ISAM file may not exist yet → use a no-op reader so the
        // Spring context loads. Phase 2 wires the real binary parser.
        ItemReader<IsamRecord> reader;
        if (java.nio.file.Files.exists(isamFile)) {
            try {
                reader = new IsamFileReader(isamFile, 64,
                    new String[]{"cal_date", "day_type", "holiday_name"});
            } catch (Exception e) {
                reader = () -> null; // no-op: ISAM file present but unreadable
            }
        } else {
            reader = () -> null; // no-op: ISAM file absent (Phase 1)
        }
        return new StepBuilder("loadCalendar", jobRepository)
            .<IsamRecord, IsamRecord>chunk(1000, txManager)
            .reader(reader)
            .processor(record -> record)
            .writer(calendarWriter)
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
