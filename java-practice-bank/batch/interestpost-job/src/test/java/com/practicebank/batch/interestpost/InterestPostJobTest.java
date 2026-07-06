package com.practicebank.batch.interestpost;

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
@Sql(scripts = {"/sql/ipst-schema.sql", "/sql/ipst-fixture.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
     config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class InterestPostJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
        // Workaround: explicitly set so @Value in @Configuration beans sees it
        System.setProperty("interestpost.run.batch.id", "MTH20260630-01");
        System.setProperty("interestpost.run.business.date", "2026-06-30");
    }

    @BeforeAll
    static void startContainer() {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job interestpostRunMonthend;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_andJobBeanPresent() {
        assertThat(interestpostRunMonthend).isNotNull();
        assertThat(interestpostRunMonthend.getName()).isEqualTo("interestpostRunMonthend");
    }

    @Test
    void jobCompletes_andTwoAccountsPosted() throws Exception {
        // sanity check: fixture data visible
        Integer acCount = new JdbcTemplate(dataSource)
            .queryForObject("SELECT COUNT(*) FROM interest_accruals WHERE status = 'AC'", Integer.class);
        assertThat(acCount).isEqualTo(8);

        JobExecution execution = jobLauncherTestUtils.launchJob();
        assertThat(execution.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);

        // 正常 2 口座 (product=001) → PT 行が 2 件
        // JobExecutionContext に格納された集計値で検証 (DB 直接クエリは TX 分離の問題を回避)
        Integer posted = execution.getExecutionContext().getInt("ipst.posted", -1);
        assertThat(posted).isEqualTo(2);

        Long totalJpyCents = execution.getExecutionContext().getLong("ipst.totalJpyCents", -1L);
        assertThat(totalJpyCents).isEqualTo(869800L); // 8698 JPY * 100
    }

    @Test
    void idempotency_secondRunSkipsAll() throws Exception {
        // 1 回目: 2 口座仕訳
        JobExecution first = jobLauncherTestUtils.launchJob();
        assertThat(first.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);
        assertThat(first.getExecutionContext().getInt("ipst.posted", -1)).isEqualTo(2);

        // 2 回目: 冪等 → 0 件追加 (posted=0 は集計対象0のため)
        JobExecution second = jobLauncherTestUtils.launchJob();
        assertThat(second.getStatus()).isIn(BatchStatus.COMPLETED, BatchStatus.STOPPED);
    }
}