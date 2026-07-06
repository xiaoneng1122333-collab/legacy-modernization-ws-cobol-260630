package com.practicebank.batch.statement;

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
@Sql(scripts = {"/sql/batch-schema.sql", "/sql/stmt-schema.sql", "/sql/stmt-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class StmtGenerateJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
        System.setProperty("statement.generate.batch.id", "STM2026061301");
        System.setProperty("statement.generate.business.date", "2026-06-13");
        System.setProperty("statement.generate.mode", "D");
        System.setProperty("statement.generate.output-filename", "/tmp/stmt-test-output/statement.rpt");
        System.setProperty("statement.generate.skip-inactive", "false");
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job stmtGenerateRun;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(stmtGenerateRun).isNotNull();
        assertThat(stmtGenerateRun.getName()).isEqualTo("stmtGenerateRun");
    }

    @Test
    void jobCompletes_andFiveAccountsProcessed() throws Exception {
        // fixture: 5 accounts with status='A' and balances
        Integer accountCount = new JdbcTemplate(dataSource)
            .queryForObject("SELECT COUNT(*) FROM accounts WHERE acct_status = 'A'", Integer.class);
        assertThat(accountCount).isEqualTo(5);

        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        // 5 口座 status='A' balances join が取得されるはず
        Integer processed = execution.getExecutionContext().getInt("stmt.processed", -1);
        assertThat(processed).isEqualTo(5);
    }
}
