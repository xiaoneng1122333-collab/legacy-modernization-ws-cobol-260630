package com.practicebank.batch.interestpost.config;

import com.practicebank.batch.interestpost.domain.AccrualSnapshot;
import com.practicebank.batch.interestpost.domain.InterestPosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * IPST-RUN-MONTHEND 相当の Spring Batch Step.
 *
 * <p>reader → processor → writer の 3 ステージ:</p>
 * <ul>
 *   <li>reader: interest_accruals から AC 行をアカウント単位で集計したスナップショット行を読み取る</li>
 *   <li>processor: product="001" / 口座存在 / 冪等 / 金額>0 でフィルタし InterestPosting インスタンスを生成。スキップ時は null.</li>
 *   <li>writer: transactions INSERT. 続いて Spring トランザクション内で postings 挿入 + balances 更新 + interest_accruals AC→PT 更新.</li>
 * </ul>
 */
@Configuration
public class InterestPostStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(InterestPostStepConfig.class);

    @Value("${interestpost.run.batch.id:#{null}}")
    private String configuredBatchId;

    @Value("${interestpost.run.business.date:#{null}}")
    private String businessDateStr;

    @Value("${interestpost.run.sys-expense-account:9999999999999}")
    private String sysExpenseAccount;

    /**
     * interest_accruals の AC 行を account_number 単位で集計して返す reader.
     * SQL で GROUP BY して IPST-RUN-MONTHEND の IPSTCUR カーソル相当を生成.
     */
    @Bean
    public JdbcCursorItemReader<AccrualSnapshot> accrualReader(DataSource dataSource) {
        final String sql =
            "SELECT account_number, product_code, " +
            "       SUM(accrued_jpy) AS accrued_jpy, " +
            "       COUNT(*)         AS ac_row_count " +
            "  FROM interest_accruals " +
            " WHERE status = 'AC' " +
            "   AND business_date <= ? " +
            " GROUP BY account_number, product_code " +
            " ORDER BY account_number";
        LocalDate businessDate = businessDateStr != null ? LocalDate.parse(businessDateStr) : LocalDate.now();
        return new JdbcCursorItemReaderBuilder<AccrualSnapshot>()
            .name("accrualReader")
            .dataSource(dataSource)
            .sql(sql)
            .queryArguments(java.sql.Date.valueOf(businessDate))
            .rowMapper(InterestPostStepConfig::mapRow)
            .build();
    }

    private static AccrualSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AccrualSnapshot(
            rs.getString("account_number"),
            rs.getString("product_code"),
            rs.getBigDecimal("accrued_jpy"),
            rs.getInt("ac_row_count")
        );
    }

    /**
     * プロセッサ: IPST-RUN-MONTHEND のスナップショット処理フローに相当.
     * <ul>
     *   <li>product_code != "001" → CTR-PROD++ 相当 (null return)</li>
     *   <li>accrued_jpy <= 0 → null</li>
     *   <li>同一ジョブ内で既に emit → CTR-ALREADY++ 相当 (null)</li>
     *   <li>balances に口座不在 → CTR-CLOSED++ 相当 (null)</li>
     *   <li>DB 重複 (source_batch_id+account_number) → CTR-ALREADY++ 相当 (null)</li>
     * </ul>
     */
    @Bean
    public ItemProcessor<AccrualSnapshot, InterestPosting> postProcessor(DataSource dataSource) {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        final Set<String> emittedAccounts = new HashSet<>();
        return snapshot -> {
            // product filter (CTR-PROD++)
            if (!"001".equals(snapshot.productCode())) {
                LOG.debug("skip product={} acct={}", snapshot.productCode(), snapshot.accountNumber());
                return null;
            }
            // amount <= 0 (CTR-HELPER++ 相当)
            if (snapshot.accruedJpy() == null || snapshot.accruedJpy().compareTo(BigDecimal.ZERO) <= 0) {
                LOG.debug("skip non-positive accrued={} acct={}", snapshot.accruedJpy(), snapshot.accountNumber());
                return null;
            }
            // within-job duplicate guard
            if (!emittedAccounts.add(snapshot.accountNumber())) {
                LOG.debug("skip within-job dup acct={}", snapshot.accountNumber());
                return null;
            }
            // balance existence (CTR-CLOSED++) — use queryForList for null-safe lookup
            var balRows = jdbc.queryForList(
                "SELECT 1 FROM balances WHERE account_number = ? LIMIT 1",
                Integer.class, snapshot.accountNumber());
            if (balRows.isEmpty()) {
                LOG.debug("skip closed acct={}", snapshot.accountNumber());
                return null;
            }
            // DB-level idempotency (CTR-ALREADY++)
            if (configuredBatchId != null && !configuredBatchId.isBlank()) {
                var dupRows = jdbc.queryForList(
                    "SELECT 1 FROM transactions WHERE source_batch_id = ? AND account_number = ? LIMIT 1",
                    Integer.class, configuredBatchId, snapshot.accountNumber());
                if (!dupRows.isEmpty()) {
                    LOG.debug("skip DB dup acct={} batch={}", snapshot.accountNumber(), configuredBatchId);
                    return null;
                }
            }
            LocalDate bizDate = businessDateStr != null
                ? LocalDate.parse(businessDateStr)
                : LocalDate.now();
            // txn_id / source_seq — deterministic per account within this run
            int seq = emittedAccounts.size();
            String txnId = String.format("IPST%04d%08d",
                java.time.Year.now().getValue(), 1000L + seq);
            return new InterestPosting(
                txnId,
                snapshot.accountNumber(),
                sysExpenseAccount,
                snapshot.accruedJpy(),
                bizDate,
                configuredBatchId != null ? configuredBatchId : "",
                snapshot.acRowCount(),
                seq
            );
        };
    }

    /**
     * ライタ: transactions INSERT.
     * Postings や balances / accruals 別更新は Phase 2 別の Step / Tasklet に分離する.
     */
    @Bean
    public JdbcBatchItemWriter<InterestPosting> postWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<InterestPosting>()
            .dataSource(dataSource)
            .sql(
                "INSERT INTO transactions " +
                "(txn_id, business_date, system_ts, category, account_number, " +
                " counter_account_number, amount_jpy, currency, description, " +
                " source_system, source_batch_id, source_seq, status, reversal_of, created_by, created_ts) " +
                "VALUES (?, ?, ?, '60', ?, ?, ?, 'JPY', 'interest-posting', 'IPST', ?, ?, 'PT', NULL, 'ipst-job', NOW())")
            .itemPreparedStatementSetter((posting, ps) -> {
                ps.setString(1, posting.txnId());
                ps.setDate(2, java.sql.Date.valueOf(posting.businessDate()));
                ps.setTimestamp(3, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                ps.setString(4, posting.accountNumber());
                ps.setString(5, posting.sysExpenseAccount());
                ps.setBigDecimal(6, posting.amountJpy());
                ps.setString(7, posting.sourceBatchId());
                ps.setInt(8, posting.sourceSeq());
            })
            .build();
    }

    /**
     * メインの利息仕訳ステップ.
     */
    @Bean
    public Step runMonthend(JobRepository jobRepository,
                            PlatformTransactionManager txManager,
                            JdbcCursorItemReader<AccrualSnapshot> accrualReader,
                            ItemProcessor<AccrualSnapshot, InterestPosting> postProcessor,
                            JdbcBatchItemWriter<InterestPosting> postWriter,
                            InterestPostStepListener interestPostStepListener) {
        return new StepBuilder("runMonthend", jobRepository)
            .<AccrualSnapshot, InterestPosting>chunk(100, txManager)
            .reader(accrualReader)
            .processor(postProcessor)
            .writer(postWriter)
            .listener((ItemWriteListener<InterestPosting>) interestPostStepListener)
            .listener((org.springframework.batch.core.StepExecutionListener) interestPostStepListener)
            .build();
    }
}
