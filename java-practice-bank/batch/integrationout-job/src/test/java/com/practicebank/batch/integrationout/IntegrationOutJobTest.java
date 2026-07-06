package com.practicebank.batch.integrationout;

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
@Sql(scripts = {"/sql/into-schema.sql", "/sql/into-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class IntegrationOutJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
        System.setProperty("integrationout.drain.source-filename", "");
        System.setProperty("integrationout.drain.max-records", "10000");
        System.setProperty("integrationout.drain.mode", "M");
        System.setProperty("integrationout.publish.event-type", "autodebit.failed");
        System.setProperty("integrationout.publish.business-date", "2026-07-06");
        System.setProperty("integrationout.publish.account", "0001234567890");
        System.setProperty("integrationout.publish.amount-jpy", "5000");
        System.setProperty("integrationout.publish.reason", "NF");
        System.setProperty("integrationout.publish.mode", "M");
        System.setProperty("integrationout.mock.broker", "true");
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job integrationOutJob;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(integrationOutJob).isNotNull();
        assertThat(integrationOutJob.getName()).isEqualTo("integrationOutJob");
    }

    @Test
    void jobCompletes_withEmptyFilename_returnsInvalidInput() throws Exception {
        // drain step: source-filename 空 → status=08 (INVALID-INPUT)
        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        String drainStatus = execution.getExecutionContext().getString("into.status", "");
        assertThat(drainStatus).isEqualTo("08");
    }

    @Test
    void publishEventStep_generatesEnvelope() throws Exception {
        // publish step: 正常入力 → status=00, eventId が UUID 形式
        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        String pubStatus = execution.getExecutionContext().getString("into.publish.status", "");
        assertThat(pubStatus).isEqualTo("00");

        String eventId = execution.getExecutionContext().getString("into.publish.eventId", "");
        assertThat(eventId).isNotBlank();
        // UUID v4 形式 (8-4-4-4-12)
        assertThat(eventId).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }
}
