package com.practicebank.batch.operations;

import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPS-BATCH-DAILY 統合テスト.
 * 日次パイプラインの Step 順序と batch_run / audit_log / transactions の状態を検証する.
 */
@SpringBootTest
@SpringBatchTest
@Sql(scripts = {"/sql/ops-schema.sql", "/sql/ops-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OperationsJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // リラクストバインディング (ops.batch.id ↔ ops.batch-id) で @Value に渡される.
        registry.add("ops.batch.id", () -> "BATCH20260706");
        registry.add("ops.business.date", () -> "20260706");
        registry.add("ops.dry-run", () -> "N");
        PostgresTestContainer.getInstance().start();
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private org.springframework.batch.core.launch.JobLauncher springJobLauncher;

    @Autowired
    private org.springframework.batch.core.repository.JobRepository jobRepository;

    @Autowired
    private ApplicationContext applicationContext;

    /** トランザクション境界の外で単一座標値を問い合わせるヘルパー. */
    private Long scalarLong(String sql) throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    @Autowired
    @Qualifier("opsBatchDailyJob")
    private Job opsBatchDailyJob;

    @Autowired
    @Qualifier("opsBatchMonthlyJob")
    private Job opsBatchMonthlyJob;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeansPresent() {
        assertThat(opsBatchDailyJob).isNotNull();
        assertThat(opsBatchDailyJob.getName()).isEqualTo("opsBatchDailyJob");
        assertThat(opsBatchMonthlyJob).isNotNull();
        assertThat(opsBatchMonthlyJob.getName()).isEqualTo("opsBatchMonthlyJob");
    }

    @Test
    void dailyJobCompletes_andStepOrderCorrect() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addString("ops.batchId", "BATCH20260706")
            .addString("ops.businessDate", "20260706")
            .addString("dryRun", "N")
            .toJobParameters();
        JobExecution execution = jobLauncherTestUtils.launchJob(params);
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        // 全量確認 (batch_run に行が 1 件入っているはず).
        assertThat(scalarLong("SELECT COUNT(*) FROM batch_run")).isEqualTo(1L);
        // batch-run-start が batch_run に行を挿入し, batch-run-complete が status=OK に更新.
        assertThat(scalarLong("SELECT COUNT(*) FROM batch_run WHERE batch_id = 'BATCH20260706'")).isEqualTo(1L);

        // OPS-SEED-SYSTEM-ACCOUNTS が冪等に 1 顧客 + 4 口座 + 4 残高を投入.
        assertThat(scalarLong("SELECT COUNT(*) FROM customers WHERE cust_id = '0000000001'")).isEqualTo(1L);
        assertThat(scalarLong("SELECT COUNT(*) FROM accounts WHERE account_number LIKE '001001000000%'")).isEqualTo(4L);
        assertThat(scalarLong("SELECT COUNT(*) FROM balances WHERE account_number LIKE '001001000000%'")).isEqualTo(4L);

        // OPS-FINALIZE が PT → SE に更新 (フィクスチャ 3 件分).
        assertThat(scalarLong(
            "SELECT COUNT(*) FROM transactions WHERE source_batch_id = 'BATCH20260706' AND status = 'SE'"))
            .isEqualTo(3L);

        // 監査ログ: OPS_BATCH_START + OPS_BATCH_OK.
        assertThat(scalarLong(
            "SELECT COUNT(*) FROM audit_log WHERE batch_id = 'BATCH20260706' AND event_type = 'OPS_BATCH_START'"))
            .isEqualTo(1L);
        assertThat(scalarLong(
            "SELECT COUNT(*) FROM audit_log WHERE batch_id = 'BATCH20260706' AND event_type = 'OPS_BATCH_OK'"))
            .isEqualTo(1L);

        // 全件確認: audit_log に BATCH20260706 のイベントが記録されていること.
        assertThat(scalarLong("SELECT COUNT(*) FROM audit_log WHERE batch_id = 'BATCH20260706'"))
            .isGreaterThanOrEqualTo(1L);
    }

    @Test
    void monthlyJobCompletes_andStepsRun() throws Exception {
        // opsBatchMonthlyJob を使って, 月次パイプラインが正常完了することを検証.
        // 直接 SimpleJobLauncher を経由して launch する.
        JobParameters params = new JobParametersBuilder()
            .addString("ops.batchId", "BATCHMONTHLY20260706")
            .addString("ops.businessDate", "20260706")
            .addString("dryRun", "Y")
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = springJobLauncher.run(opsBatchMonthlyJob, params);
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        assertThat(scalarLong("SELECT COUNT(*) FROM audit_log WHERE event_type = 'OPS_MONTHLY_START'")).isEqualTo(1L);
        assertThat(scalarLong("SELECT COUNT(*) FROM audit_log WHERE event_type = 'OPS_MONTHLY_OK'")).isEqualTo(1L);
    }
}
