package com.practicebank.verify.comparator;

import com.practicebank.verify.comparator.model.CompareReport;
import com.practicebank.verify.comparator.model.DiffEntry;
import com.practicebank.verify.comparator.service.ComparatorService;
import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = {"/sql/comparator-schema.sql", "/sql/comparator-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class ComparatorServiceTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer container = PostgresTestContainer.getInstance();
        container.start();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.java.datasource.url", container::getJdbcUrl);
        registry.add("spring.java.datasource.username", container::getUsername);
        registry.add("spring.java.datasource.password", container::getPassword);
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private ComparatorService service;

    @Test
    void compare_sameDate_allMatch() {
        CompareReport report = service.compare(LocalDate.of(2026, 7, 6));

        assertThat(report).isNotNull();
        assertThat(report.businessDate()).isEqualTo("2026-07-06");
        assertThat(report.overallStatus()).isEqualTo("MATCH");
        assertThat(report.mismatchCount()).isZero();
        assertThat(report.totalDiff()).isZero();

        // 各テーブル MATCH 確認
        for (DiffEntry e : report.tables()) {
            assertThat(e.status())
                .as("table " + e.tableName() + " should MATCH")
                .isEqualTo("MATCH");
        }
    }

    @Test
    void compare_differentDate_onlyCobol() {
        // 2026-07-07 は未投入 → 全テーブル 0 = 0 で MATCH
        CompareReport report = service.compare(LocalDate.of(2026, 7, 7));
        assertThat(report.overallStatus()).isEqualTo("MATCH");
    }

    @Test
    void compare_reportContainsExpectedTables() {
        CompareReport report = service.compare(LocalDate.of(2026, 7, 6));
        long txn = report.tables().stream()
            .filter(e -> e.tableName().equals("transactions"))
            .findFirst()
            .map(DiffEntry::cobolCount)
            .orElse(0L);
        assertThat(txn).isEqualTo(5L);

        long bal = report.tables().stream()
            .filter(e -> e.tableName().equals("balances"))
            .findFirst()
            .map(DiffEntry::javaCount)
            .orElse(0L);
        assertThat(bal).isEqualTo(3L);

        long cust = report.tables().stream()
            .filter(e -> e.tableName().equals("customers"))
            .findFirst()
            .map(DiffEntry::cobolCount)
            .orElse(0L);
        assertThat(cust).isEqualTo(2L);
    }
}
