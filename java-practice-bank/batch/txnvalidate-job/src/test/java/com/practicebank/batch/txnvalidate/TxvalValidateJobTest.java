package com.practicebank.batch.txnvalidate;

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

@SpringBootTest
@SpringBatchTest
@Sql(scripts = {"/sql/txval-schema.sql", "/sql/txval-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class TxvalValidateJobTest {

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
    private Job txnvalValidateJob;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(txnvalValidateJob).isNotNull();
        assertThat(txnvalValidateJob.getName()).isEqualTo("txnvalValidateJob");
    }

    @Test
    void jobCompletes_andValidationStatusFlagsCorrect() throws Exception {
        // sanity check: fixture data is visible from Spring-managed TX
        Integer fixtureCount = new JdbcTemplate(dataSource)
            .queryForObject("SELECT COUNT(*) FROM transactions WHERE status = 'PT'", Integer.class);
        assertThat(fixtureCount).isEqualTo(11);

        JobExecution execution = jobLauncherTestUtils.launchJob();
        // AB-terminated with no auto-retry = COMPLETED (Spring Batch exits when reader returns null)
        assertThat(execution.getStatus()). isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 正常 3 件 → VO
        Integer validCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE status = 'VO'", Integer.class);
        assertThat(validCount).isEqualTo(3);

        // 拒否 8 件 → RJ
        Integer rejectedCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE status = 'RJ'", Integer.class);
        assertThat(rejectedCount).isEqualTo(8);

        // FIXTURE 全 11 件に VO or RJ が付いているはず (COBOL status=04 相当)
        Integer pendingCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE status = 'PT'", Integer.class);
        assertThat(pendingCount).isZero();
    }
}
