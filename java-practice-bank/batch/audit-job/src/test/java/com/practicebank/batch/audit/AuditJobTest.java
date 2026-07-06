package com.practicebank.batch.audit;

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
@Sql(scripts = {"/sql/audit-schema.sql", "/sql/audit-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class AuditJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
        // APR props
        System.setProperty("audit.rollover.operator-user", "TESTOP");
        System.setProperty("audit.rollover.retention-days", "30");
        System.setProperty("audit.rollover.dry-run", "N");
        System.setProperty("audit.rollover.enable-detach", "Y");
        System.setProperty("audit.business.date", "2026-07-06");
        // AQF props — subsystem filter set to "17-statement" to test filter path
        System.setProperty("audit.forensic.date-start", "20260601");
        System.setProperty("audit.forensic.date-end", "20260730");
        System.setProperty("audit.forensic.subsystem", "17-statement");
        System.setProperty("audit.forensic.action", "");
        System.setProperty("audit.forensic.severity", "");
        System.setProperty("audit.forensic.account-filter", "");
        System.setProperty("audit.forensic.max-rows", "1000");
        System.setProperty("audit.forensic.output-format", "CSV");
        System.setProperty("audit.forensic.output-filename", "");
        System.setProperty("audit.forensic.operator-user", "TESTOP");
        System.setProperty("audit.forensic.file-output", "false");
        // ASR props — BY-SUBSYSTEM mode to cover branch
        System.setProperty("audit.summary.date-start", "20260601");
        System.setProperty("audit.summary.date-end", "20260730");
        System.setProperty("audit.summary.mode", "S");
        System.setProperty("audit.summary.output-filename", "");
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job auditJob;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(auditJob).isNotNull();
        assertThat(auditJob.getName()).isEqualTo("auditJob");
    }

    @Test
    void jobCompletes_allStepsRun() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        // APR: 次月パーティション名が audit_log_202608 形式
        String nextPartition = execution.getExecutionContext().getString("apr.nextPartition", "");
        assertThat(nextPartition).startsWith("audit_log_");
        assertThat(execution.getExecutionContext().getString("apr.status", "")).isEqualTo("00");

        // AQF: subsystem="17-statement" → 2 件のみ
        int aqfRows = execution.getExecutionContext().getInt("aqf.rowCount", -1);
        assertThat(aqfRows).isEqualTo(2);

        // ASR: BY-SUBSYSTEM 集計
        String asrStatus = execution.getExecutionContext().getString("asr.status", "");
        assertThat(asrStatus).isEqualTo("00");
        int groups = execution.getExecutionContext().getInt("asr.groupCount", 0);
        // subsystems: 17-statement(2 rows), 20-integrationout(1), 01-txnpost(2), 03-autodebit(1) → grouped by subsystem+severity ≥3
        assertThat(groups).isGreaterThanOrEqualTo(3);
        long totalRows = execution.getExecutionContext().getLong("asr.totalRows", 0L);
        assertThat(totalRows).isEqualTo(6L);
    }
}
