package com.practicebank.batch.txnpost;

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
    "txpost.business.date=20260706",
    "txpost.report.filename=/tmp/test-txpost-report.txt"
})
@SpringBatchTest
@Sql(scripts = {"/sql/txpost-schema.sql", "/sql/txpost-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class TxpostRunBatchJobTest {

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
    private Job txpostJob;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(txpostJob).isNotNull();
        assertThat(txpostJob.getName()).isEqualTo("txpostRunBatchJob");
    }

    @Test
    void jobCompletes_andTxnsPostedCorrect() throws Exception {
        // sanity check: fixture PT transactions visible
        Integer fixtureCount = new JdbcTemplate(dataSource)
            .queryForObject("SELECT COUNT(*) FROM transactions WHERE status = 'PT'", Integer.class);
        assertThat(fixtureCount).isEqualTo(3);

        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 3 件は SETTLE (SE) に更新される
        Integer seCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE status = 'SE'", Integer.class);
        assertThat(seCount).isEqualTo(3);

        // postings は 3 × 2 = 6 行 (dual-entry DR+CR)
        Integer pstCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM postings", Integer.class);
        assertThat(pstCount).isEqualTo(6);
    }

    @Test
    void balancesUpdated_afterPosting() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        // ジョブの ExecutionContext 経由で検証 (report-step が count を取得).
        // TXPOST-REPORT-SUMMARY: PT=0 SE=3 RV=0 GRAND=3 POSTINGS=6 conservation=Y であることを確認.
        var ctx = execution.getExecutionContext();
        assertThat(ctx.getInt("txpost.se")).isEqualTo(3);
        assertThat(ctx.getInt("txpost.grand")).isEqualTo(3);
        assertThat(ctx.getInt("txpost.pstTotal")).isEqualTo(6);
        assertThat(ctx.get("txpost.conservationOk")).isEqualTo("Y");

        // 個別の DR/CR 内訳が postings テーブルに存在することを確認 (COUNT query).
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer totalPostings = jdbc.queryForObject("SELECT COUNT(*) FROM postings", Integer.class);
        assertThat(totalPostings).isEqualTo(6);
    }

    @Test
    void rerunIsIdempotent_noDuplicatePostings() throws Exception {
        // First run
        jobLauncherTestUtils.launchJob();

        // Second run — 3 件は既に SE で PT じゃないため再処理対象外
        JobExecution execution2 = jobLauncherTestUtils.launchJob();
        assertThat(execution2.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Still 6 postings, not 12
        Integer pstCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM postings", Integer.class);
        assertThat(pstCount).isEqualTo(6);

        // Still 3 SE
        Integer seCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE status = 'SE'", Integer.class);
        assertThat(seCount).isEqualTo(3);
    }
}
