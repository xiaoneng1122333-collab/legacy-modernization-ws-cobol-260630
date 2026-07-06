package com.practicebank.masters.calendar;

import com.practicebank.common.domain.DayType;
import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Sql(scripts = "/sql/calendar.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CalendarRepositoryTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Autowired
    private CalendarRepository repository;

    @Test
    void findByDate_returnsCalendarRecord() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        Calendar cal = repository.findByDate(date).orElseThrow();

        assertThat(cal.calDate()).isEqualTo(date);
        assertThat(cal.dayType()).isEqualTo(DayType.H);
        assertThat(cal.holidayName()).contains("元日");
    }
}
