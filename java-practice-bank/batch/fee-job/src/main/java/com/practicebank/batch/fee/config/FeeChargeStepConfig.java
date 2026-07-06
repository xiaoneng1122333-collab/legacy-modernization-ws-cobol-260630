package com.practicebank.batch.fee.config;

import com.practicebank.batch.fee.domain.FeePosting;
import com.practicebank.batch.fee.domain.FeeTransactionSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
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
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * FEE-CHARGE 相当の Spring Batch Step.
 *
 * <p>reader → processor → writer の 3 ステージ:</p>
 * <ul>
 *   <li>reader: transactions から category IN (30, 40) かつ status='PT' の取引をカーソル取得.</li>
 *   <li>processor: カテゴリ 30 非課金 / tier1 非課金 / 口座不在 / 残高不足 / 重複 をスキップ.
 *       スキップ時は null を返す. posted=true の FeePosting 相当 {@link FeeSkipCounters} でカウントする.</li>
 *   <li>writer: FeePosting を transactions INSERT (fee明細).</li>
 * </ul>
 */
@Configuration
public class FeeChargeStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(FeeChargeStepConfig.class);

    @Value("${fee.charge.fee-rev-account:0010010000004}")
    private String feeRevAccount;

    @Value("${fee.charge.batch.id:#{null}}")
    private String configuredBatchId;

    @Value("${fee.charge.business.date:#{null}}")
    private String businessDateStr;

    /**
     * transactions テーブルから カテゴリ 30/40 の PT 行を取得する reader.
     * スキャン上限は 500 行 (COBOL WS-SNAPSHOT OCCURS 500).
     */
    @Bean
    @StepScope
    public JdbcCursorItemReader<FeeTransactionSnapshot> feeReader(DataSource dataSource) {
        final StringBuilder sql = new StringBuilder(
            "SELECT txn_id, account_number, amount_jpy, category, source_batch_id, source_seq " +
            "  FROM transactions " +
            " WHERE category IN ('30', '40') AND status = 'PT'" +
            "   AND (description NOT LIKE 'fee-%' OR description IS NULL)");
        if (businessDateStr != null && !businessDateStr.isBlank()) {
            sql.append(" AND business_date = CAST(? AS DATE)");
        }
        sql.append(" ORDER BY txn_id LIMIT 500");

        JdbcCursorItemReaderBuilder<FeeTransactionSnapshot> builder =
            new JdbcCursorItemReaderBuilder<FeeTransactionSnapshot>()
                .name("feeReader")
                .dataSource(dataSource)
                .sql(sql.toString())
                .rowMapper(FeeChargeStepConfig::mapRow);
        if (businessDateStr != null && !businessDateStr.isBlank()) {
            builder.queryArguments(Date.valueOf(LocalDate.parse(businessDateStr)));
        }
        return builder.build();
    }

    private static FeeTransactionSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new FeeTransactionSnapshot(
            rs.getString("txn_id"),
            rs.getString("account_number"),
            rs.getBigDecimal("amount_jpy"),
            rs.getString("category"),
            rs.getString("source_batch_id"),
            rs.getInt("source_seq")
        );
    }

    /**
     * プロセッサ: FEE-LOOKUP-BY-TIER + ACCT-EXISTS + 残高チェック + 重複排除.
     * <p>スキップ時は null を返す (Spring Batch が writer への送付をスキップ).
     * スキップ理由は共有 {@link FeeSkipCounters} に記録する.</p>
     */
    @Bean
    public ItemProcessor<FeeTransactionSnapshot, FeePosting> feeProcessor(DataSource dataSource,
                                                                          FeeSkipCounters counters,
                                                                          FeeProcessorState processorState) {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        final LocalDate bizDate = businessDateStr != null ? LocalDate.parse(businessDateStr) : LocalDate.now();
        final String batchId = configuredBatchId != null ? configuredBatchId : "";

        return snapshot -> {
            // category 30 は非課金 (design test #2)
            if (snapshot.isCategoryNonChargeable()) {
                counters.increment("NF");
                LOG.debug("skip category30 txn={}", snapshot.txnId());
                return null;
            }

            // FEE-LOOKUP-BY-TIER 相当: snapshot.txn_id 末尾 1 文字で tier が決まる (seed 用簡易ロジック)
            String tier = resolveTier(snapshot);
            BigDecimal feeJpy = lookupFee(dataSource, snapshot.category(), tier, bizDate);

            // tier1 手数料 0 → CTR-NO-FEE++ (design test #3)
            if (feeJpy == null || feeJpy.compareTo(BigDecimal.ZERO) <= 0) {
                counters.increment("NF");
                LOG.debug("skip zero fee txn={} tier={}", snapshot.txnId(), tier);
                return null;
            }

            // ACCT-EXISTS: balances テーブルに口座が存在するか
            Long balance;
            try {
                balance = jdbc.queryForObject(
                    "SELECT balance_jpy FROM balances WHERE account_number = ?",
                    Long.class, snapshot.accountNumber());
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                counters.increment("CL");
                LOG.debug("skip closed acct={}", snapshot.accountNumber());
                return null;
            }

            // 残高不足 (NSF) — 手数料 > 残高
            if (balance < feeJpy.longValue()) {
                counters.increment("NS");
                LOG.debug("skip NSF acct={} bal={} fee={}", snapshot.accountNumber(), balance, feeJpy);
                return null;
            }

            // 同一 snapshot.accountNumber が既に処理済み → CTR-ALREADY++
            if (!processorState.markSeen(snapshot.accountNumber())) {
                counters.increment("AL");
                LOG.debug("skip within-job dup acct={}", snapshot.accountNumber());
                return null;
            }

            // DB-level 冪等: (source_batch_id, description) で fee明細が存在するか
            if (!batchId.isBlank()) {
                Integer dupCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transactions " +
                    "WHERE source_batch_id = ? AND description = ?",
                    Integer.class, batchId,
                    "fee-" + snapshot.category() + "-" + snapshot.txnId());
                if (dupCount != null && dupCount > 0) {
                    counters.increment("AL");
                    LOG.debug("skip DB dup acct={} txn={}", snapshot.accountNumber(), snapshot.txnId());
                    return null;
                }
            }

            int seq = processorState.size();
            String txnId = generateTxnId(snapshot, bizDate, seq);
            return new FeePosting(
                txnId,
                snapshot.accountNumber(),
                feeRevAccount,
                feeJpy,
                bizDate,
                batchId,
                seq,
                "fee-" + snapshot.category() + "-" + snapshot.txnId()
            );
        };
    }

    /**
     * snapshot から tier を決定する簡易ロジック.
     * txn_id 末尾 1 文字で tier を指定可能 (seed で tier1/tier2/tier3 を区別).
     */
    private String resolveTier(FeeTransactionSnapshot snapshot) {
        String txnId = snapshot.txnId();
        if (txnId == null || txnId.length() < 1) return "1";
        char last = txnId.charAt(txnId.length() - 1);
        return switch (last) {
            case '1' -> "1";
            case '2' -> "2";
            case '3' -> "3";
            default -> "1";
        };
    }

    /**
     * fee_schedules テーブルから fee_jpy を検索.
     * 該当がなければ BigDecimal.ZERO を返す (非課金).
     */
    private BigDecimal lookupFee(DataSource dataSource, String category, String tier, LocalDate bizDate) {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            return jdbc.queryForObject(
                "SELECT fee_jpy FROM fee_schedules " +
                "WHERE category = ? AND tier = ? AND effective_date <= ? " +
                "ORDER BY effective_date DESC LIMIT 1",
                BigDecimal.class, category, tier, Date.valueOf(bizDate));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * fee明細用 deterministic txn_id 生成.
     * フォーマット: F + YYMMDD + 'C' + seq 4桁 + acct 下4桁.
     */
    private String generateTxnId(FeeTransactionSnapshot snapshot, LocalDate bizDate, int seq) {
        String acctSuffix = snapshot.accountNumber();
        if (acctSuffix.length() > 4) {
            acctSuffix = acctSuffix.substring(acctSuffix.length() - 4);
        }
        int dateNum = bizDate.getYear() % 100 * 10000 + bizDate.getMonthValue() * 100 + bizDate.getDayOfMonth();
        return "F" + String.format("%06d", dateNum) + "C" + String.format("%04d", seq) + acctSuffix;
    }

    /**
     * ライタ: transactions に fee明細 INSERT.
     */
    @Bean
    public JdbcBatchItemWriter<FeePosting> feeWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<FeePosting>()
            .dataSource(dataSource)
            .sql(
                "INSERT INTO transactions " +
                "(txn_id, business_date, system_ts, category, account_number, " +
                " counter_account_number, amount_jpy, currency, description, " +
                " source_system, source_batch_id, source_seq, status, reversal_of, created_by, created_ts) " +
                "VALUES (?, ?, ?, '50', ?, ?, ?, 'JPY', ?, 'FEE', ?, ?, 'PT', NULL, 'fee-job', NOW())")
            .itemPreparedStatementSetter((posting, ps) -> {
                ps.setString(1, posting.txnId());
                ps.setDate(2, Date.valueOf(posting.businessDate()));
                ps.setTimestamp(3, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                ps.setString(4, posting.accountNumber());
                ps.setString(5, posting.counterAccountNumber());
                ps.setBigDecimal(6, posting.feeJpy());
                ps.setString(7, posting.description());
                ps.setString(8, posting.sourceBatchId());
                ps.setInt(9, posting.sourceSeq());
            })
            .build();
    }

    /** FEE-CHARGE メイン Step. */
    @Bean
    public Step feeCharge(JobRepository jobRepository,
                          PlatformTransactionManager txManager,
                          JdbcCursorItemReader<FeeTransactionSnapshot> feeReader,
                          ItemProcessor<FeeTransactionSnapshot, FeePosting> feeProcessor,
                          JdbcBatchItemWriter<FeePosting> feeWriter,
                          FeeChargeStepListener stepListener) {
        return new StepBuilder("feeCharge", jobRepository)
            .<FeeTransactionSnapshot, FeePosting>chunk(100, txManager)
            .reader(feeReader)
            .processor(feeProcessor)
            .writer(feeWriter)
            .listener((org.springframework.batch.core.ItemWriteListener<FeePosting>) stepListener)
            .listener((org.springframework.batch.core.StepExecutionListener) stepListener)
            .build();
    }
}
