package com.practicebank.batch.txnsortmerge;

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
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@Sql(scripts = {"/sql/txsm-schema.sql", "/sql/txsm-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class TxsmSortMergeJobTest {

    @DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("txsmSortMergeJobDefinition")
    private Job txsmSortMergeJob;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(txsmSortMergeJob).isNotNull();
        assertThat(txsmSortMergeJob.getName()).isEqualTo("txsmSortMergeJob");
    }

    @Test
    void jobCompletes_sortProducesSortedRows_mergeProducesReadyRows() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // fixture: 3 VO + 0 SE (事前条件)
        Integer voBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE status = 'VO'", Integer.class);
        assertThat(voBefore).isEqualTo(3);

        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        // SORT 出力: 3 件, payer-acct → seq 昇順.
        Integer sortedCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM txn_sorted_txn", Integer.class);
        assertThat(sortedCount).isEqualTo(3);

        // ソート順検証 (account_number ASC, source_seq ASC).
        var sortedRows = jdbc.query(
            "SELECT account_number, source_seq FROM txn_sorted_txn ORDER BY sorted_seq",
            (rs, n) -> rs.getString("account_number") + ":" + rs.getInt("source_seq"));
        assertThat(sortedRows).containsExactly(
            "0001234567890:2",
            "0001234567890:3",
            "0009876543210:1"
        );

        // 入力ステータスは VO → SE (sorted).
        Integer voAfter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE status = 'VO'", Integer.class);
        assertThat(voAfter).isZero();
        Integer seAfter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE status = 'SE'", Integer.class);
        // SE は mergeStep で MG に更新されるため、ここでは 0 (MG = 3) になるはず.
        Integer mgAfter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE status = 'MG'", Integer.class);
        assertThat(mgAfter).isEqualTo(3);

        // MERGE 出力: disjoint キー (3 sorted + 2 recon) = 5.
        Integer readyCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM txn_ready_txn", Integer.class);
        assertThat(readyCount).isEqualTo(5);

        // マージ順検証 (account_number ASC, source_seq ASC).
        var readyRows = jdbc.query(
            "SELECT account_number, source_seq, source_kind FROM txn_ready_txn ORDER BY account_number, source_seq",
            (rs, n) -> rs.getString("account_number") + ":" + rs.getInt("source_seq") + ":" + rs.getString("source_kind"));
        assertThat(readyRows).containsExactly(
            "0001111111111:1:RECON",
            "0001234567890:2:SORTED",
            "0001234567890:3:SORTED",
            "0005555555555:2:RECON",
            "0009876543210:1:SORTED"
        );

        // 保存量不変条件: sin + rin = out + dup-records → 3 + 2 = 5 + 0.
        Integer errorCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM txn_error_record", Integer.class);
        assertThat(errorCount).isZero();
    }
}
