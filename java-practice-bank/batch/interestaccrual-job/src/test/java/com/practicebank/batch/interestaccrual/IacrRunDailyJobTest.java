package com.practicebank.batch.interestaccrual;

import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "iacr.business.date=20260706",
    "iacr.report.filename=/tmp/test-iacr-report.txt"
})
@SpringBatchTest
@Sql(scripts = {"/sql/iacr-schema.sql", "/sql/iacr-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class IacrRunDailyJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job iacrBatchJob;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(iacrBatchJob).isNotNull();
        assertThat(iacrBatchJob.getName()).isEqualTo("iacrRunDailyJob");
    }

    @Test
    void jobCompletes_andAccrualsInsertedCorrect() throws Exception {
        // sanity check: fixture balances visible
        Integer fixtureCount = new JdbcTemplate(dataSource)
            .queryForObject("SELECT COUNT(*) FROM balances", Integer.class);
        assertThat(fixtureCount).isEqualTo(8);

        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 正常 3 件 (balance > 0, non-system) → INSERT
        Integer acCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM interest_accruals WHERE business_date = '2026-07-06'", Integer.class);
        assertThat(acCount).isEqualTo(3);

        // 3 件は PT (pending) ステータスで INSERT される
        Integer ptCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM interest_accruals WHERE business_date = '2026-07-06' AND status = 'PT'",
            Integer.class);
        assertThat(ptCount).isEqualTo(3);
    }

    @Test
    void rerunIsIdempotent_noDuplicateAccruals() throws Exception {
        // First run
        jobLauncherTestUtils.launchJob();

        // Second run — ON CONFLICT DO NOTHING prevents duplicate
        JobExecution execution2 = jobLauncherTestUtils.launchJob();
        assertThat(execution2.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Still 3 records, not 6
        Integer acCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM interest_accruals WHERE business_date = '2026-07-06'", Integer.class);
        assertThat(acCount).isEqualTo(3);
    }
}
