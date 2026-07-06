package com.practicebank.batch.autodebit;

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
@Sql(scripts = {"/sql/autodebit-schema.sql", "/sql/autodebit-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class AutodebitJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("autodebit.batch.id", () -> "BATCH20260706");
        registry.add("autodebit.business.date", () -> "2026-07-06");
        PostgresTestContainer.getInstance().start();
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job autodebitRunJob;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(autodebitRunJob).isNotNull();
        assertThat(autodebitRunJob.getName()).isEqualTo("autodebitJob");
    }

    @Test
    void jobCompletes_andPostResultsCorrect() throws Exception {
        // sanity check: fixture data is visible from Spring-managed TX
        Integer fixtureCount = new JdbcTemplate(dataSource)
            .queryForObject("SELECT COUNT(*) FROM autodebit_schedules WHERE status = 'AC'", Integer.class);
        assertThat(fixtureCount).isEqualTo(4);

        // verify balances + tables are accessible
        Integer balCount = new JdbcTemplate(dataSource)
            .queryForObject("SELECT COUNT(*) FROM balances", Integer.class);
        assertThat(balCount).isEqualTo(1);

        // verify data is visible right before launching the job
        Integer dueCount = new JdbcTemplate(dataSource)
            .queryForObject("SELECT COUNT(*) FROM autodebit_schedules WHERE status = 'AC' AND next_due_date <= '2026-07-06'", Integer.class);
        assertThat(dueCount).isEqualTo(3);

        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 正常 ACCT 指令 (残高十分) は POST 成功 → status AC, consecutive_failures=0
        Integer posted = jdbc.queryForObject(
            "SELECT COUNT(*) FROM autodebit_schedules " +
            "WHERE instruction_id = 'AD2001' AND status = 'AC' AND consecutive_failures = 0",
            Integer.class);
        assertThat(posted).isEqualTo(1); // AD2001

        // 残高不足の指令 (AD2002) は POST 失敗 → consecutive_failures > 0
        Integer failedNf = jdbc.queryForObject(
            "SELECT COUNT(*) FROM autodebit_schedules " +
            "WHERE instruction_id = 'AD2002' AND consecutive_failures > 0", Integer.class);
        assertThat(failedNf).isEqualTo(1);

        // 口座異常指令 (AD2003, balances に存在しない) は CL → TM に遷移
        Integer terminated = jdbc.queryForObject(
            "SELECT COUNT(*) FROM autodebit_schedules WHERE instruction_id = 'AD2003' AND status = 'TM'",
            Integer.class);
        assertThat(terminated).isEqualTo(1);

        // 期限未到来の指令 (AD2004) は処理対象外 (変更なし)
        Integer unchanged = jdbc.queryForObject(
            "SELECT COUNT(*) FROM autodebit_schedules " +
            "WHERE instruction_id = 'AD2004' AND status = 'AC' AND consecutive_failures = 0 " +
            "AND last_attempt_date IS NULL",
            Integer.class);
        assertThat(unchanged).isEqualTo(1);

        // 実行件数 3 件 (AD2001/AD2002/AD2003 が対象, AD2004 は対象外)
        Integer processed = jdbc.queryForObject(
            "SELECT COUNT(*) FROM autodebit_schedules " +
            "WHERE last_attempt_date IS NOT NULL", Integer.class);
        assertThat(processed).isEqualTo(3);
    }
}
