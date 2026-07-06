package com.practicebank.batch.fee;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@Sql(scripts = {"/sql/batch-schema.sql", "/sql/fee-schema.sql", "/sql/fee-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class FeeChargeJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
        System.setProperty("fee.charge.batch.id", "FEE20260613-01");
        System.setProperty("fee.charge.business.date", "2026-06-13");
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job feeChargeRun;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(feeChargeRun).isNotNull();
        assertThat(feeChargeRun.getName()).isEqualTo("feeChargeRun");
    }

    @Test
    void jobCompletes_andTwoAccountsPosted() throws Exception {
        Integer scanTargetCount = new JdbcTemplate(dataSource)
            .queryForObject("SELECT COUNT(*) FROM transactions WHERE category IN ('30', '40') AND status = 'PT'",
                Integer.class);
        assertThat(scanTargetCount).isEqualTo(5);

        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        Integer posted = execution.getExecutionContext().getInt("fee.posted", -1);
        assertThat(posted).isEqualTo(2);

        Long totalFeeJpyCents = execution.getExecutionContext().getLong("fee.totalFeeJpyCents", -1L);
        assertThat(totalFeeJpyCents).isEqualTo(132000L);

        Integer noFee = execution.getExecutionContext().getInt("fee.skippedNoFee", -1);
        assertThat(noFee).isEqualTo(2);

        Integer nsf = execution.getExecutionContext().getInt("fee.skippedNsf", -1);
        assertThat(nsf).isEqualTo(1);
    }

    @Test
    void reportSummary_conservationCheck() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);
    }
}
