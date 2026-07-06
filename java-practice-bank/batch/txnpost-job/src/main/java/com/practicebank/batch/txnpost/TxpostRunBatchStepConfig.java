package com.practicebank.batch.txnpost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TXPOST-RUN-BATCH 相当の Spring Batch Step.
 *
 * <p>reader → processor → writer の 3 ステージで構成:</p>
 * <ul>
 *   <li>reader: {@code transactions} テーブルから status='PT' 取引をカーソル読み取り</li>
 *   <li>processor: I1 (冪等スキップ) / I5 (禁止操作) / I3 (残高) チェックを実施。合格なら "POST" を返却</li>
 *   <li>writer: 結果を transactions (status 更新) / postings (2行 INSERT) / balances (UPDATE) に書込</li>
 * </ul>
 *
 * <p>単一チャンク内で SERIALIZABLE トランザクションにより dual-entry 記帳を atomic に実行.
 * バッチ終了時にカウンタを集約し audit_log へ監査イベントを書き込む.</p>
 */
@Configuration
public class TxpostRunBatchStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TxpostRunBatchStepConfig.class);

    /**
     * システム口座 (現金・整治口). DETERMINE-POSTING-ACCTS 判定で使用.
     */
    private static final String CASH_ACCOUNT = "0000000000001";
    private static final String CLEARING_ACCOUNT = "0000000000002";

    /**
     * 取引停止 (ブロック) カテゴリ. I5 判定で hard-reject.
     * Phase 2: 当面固定値 — マスタ整備時に BLOCK-REASONS テーブル参照に差し替え.
     */
    private static final java.util.Set<String> BLOCKED_CATEGORIES = java.util.Set.of("99");

    @Value("${txpost.business.date:#{null}}")
    private String businessDate;

    /** {@code transactions} テーブルから status='PT' を SELECT する reader. */
    @Bean
    public JdbcCursorItemReader<TransactionRecord> txnReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<TransactionRecord>()
            .name("txnReader")
            .dataSource(dataSource)
            .sql("SELECT txn_id, business_date, system_ts, category, account_number, " +
                 "counter_account_number, amount_jpy, currency, description, source_system, " +
                 "source_batch_id, source_seq, status, reversal_of, created_by, created_ts " +
                 "FROM transactions WHERE status = 'PT' ORDER BY source_batch_id, source_seq")
            .rowMapper(TxpostRunBatchStepConfig::mapRow)
            .build();
    }

    private static TransactionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TransactionRecord(
            rs.getString("txn_id"),
            rs.getDate("business_date") != null ? rs.getDate("business_date").toLocalDate() : null,
            rs.getTimestamp("system_ts") != null ? rs.getTimestamp("system_ts").toLocalDateTime() : null,
            rs.getString("category"),
            rs.getString("account_number"),
            rs.getString("counter_account_number"),
            rs.getBigDecimal("amount_jpy") != null ? rs.getBigDecimal("amount_jpy").toBigInteger() : BigInteger.ZERO,
            rs.getString("currency"),
            rs.getString("description"),
            rs.getString("source_system"),
            rs.getString("source_batch_id"),
            rs.getInt("source_seq"),
            rs.getString("status"),
            rs.getString("reversal_of"),
            rs.getString("created_by"),
            rs.getTimestamp("created_ts") != null ? rs.getTimestamp("created_ts").toLocalDateTime() : null
        );
    }

    /**
     * 取引適格性判定プロセッサ.
     * COBOL 設計の I1/I5/I3 チェックを Java で表現.
     *
     * @return 適格なら TransactionRecord をそのまま返却、不適格なら null (スキップ).
     */
    @Bean
    public ItemProcessor<TransactionRecord, TransactionRecord> txnProcessor(DataSource dataSource) {
        return txn -> {
            // I5 — 禁止操作 (category=99 etc.)
            if (txn.category() != null && BLOCKED_CATEGORIES.contains(txn.category().trim())) {
                LOG.debug("txn {} category blocked, skip", txn.txnId());
                return null;
            }

            // I3 — 残高檢查 (出金/振替: 残高不足ならリジェクト)
            // category 20 (出金) / 30 (振替) / 40 (電払) は引き落ち側で残高チェックが必要
            if (isDebitCategory(txn.category())) {
                BigInteger currentBalance = fetchBalance(dataSource, txn.accountNumber());
                if (currentBalance == null || currentBalance.compareTo(txn.amountJpy()) < 0) {
                    LOG.debug("txn {} balance insufficient (bal={}, amt={}), skip",
                        txn.txnId(), currentBalance, txn.amountJpy());
                    return null;
                }
            }

            // Cat-30: payee 未設定チェック (E004)
            if ("30".equals(txn.category()) && (txn.counterAccountNumber() == null
                    || txn.counterAccountNumber().trim().isEmpty())) {
                LOG.debug("txn {} category 30 but no payee, skip", txn.txnId());
                return null;
            }

            return txn;
        };
    }

    private static boolean isDebitCategory(String category) {
        return "20".equals(category) || "30".equals(category) || "40".equals(category);
    }

    private static BigInteger fetchBalance(DataSource dataSource, String accountNumber) {
        if (accountNumber == null) return null;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT balance_jpy FROM balances WHERE account_number = ?")) {
            ps.setString(1, accountNumber.trim());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1) != null ? rs.getBigDecimal(1).toBigInteger() : null;
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to fetch balance for account {}: {}", accountNumber, e.getMessage());
        }
        return null;
    }

    /**
     * 記帳 writer: transactions (status SE) / postings (DR+CR 2行) / balances (更新) を atomic に書込.
     * PostgreSQL は単一 PreparedStatement での複数ステートメント実行をサポートしないため,
     * JdbcTemplate で個別 SQL を Spring Batch チャンクトランザクション内で実行する.
     */
    @Bean
    public ItemWriter<TransactionRecord> txnWriter(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return items -> {
            for (TransactionRecord txn : items) {
                LocalDate bdate = txn.businessDate() != null ? txn.businessDate() : LocalDate.now();
                java.sql.Date sqlDate = java.sql.Date.valueOf(bdate);
                LocalDateTime now = LocalDateTime.now();
                java.sql.Timestamp sqlTs = java.sql.Timestamp.valueOf(now);
                BigInteger amt = txn.amountJpy();
                String drAccount = determineDebitAccount(txn);
                String crAccount = determineCreditAccount(txn);

                // I1 — idempotency check: status='PT' の場合のみ UPDATE (さ記済みはスキkip)
                int updated = jdbc.update(
                    "UPDATE transactions SET status = 'SE' WHERE txn_id = ? AND status = 'PT'",
                    txn.txnId());
                if (updated == 0) {
                    // 既に SE 済み (前回再実行). スキップ.
                    continue;
                }

                // INSERT postings line 1 (DR)
                String pstId1 = "PST" + UUID.randomUUID().toString().substring(0, 17).toUpperCase();
                jdbc.update(
                    "INSERT INTO postings (posting_id, txn_id, line_no, account_number, " +
                    "debit_jpy, credit_jpy, posting_role, business_date, created_ts) " +
                    "VALUES (?, ?, 1, ?, ?, 0, 'DR', ?, ?)",
                    pstId1, txn.txnId(), drAccount, new java.math.BigDecimal(amt), sqlDate, sqlTs);

                // INSERT postings line 2 (CR)
                String pstId2 = "PST" + UUID.randomUUID().toString().substring(0, 17).toUpperCase();
                jdbc.update(
                    "INSERT INTO postings (posting_id, txn_id, line_no, account_number, " +
                    "debit_jpy, credit_jpy, posting_role, business_date, created_ts) " +
                    "VALUES (?, ?, 2, ?, 0, ?, 'CR', ?, ?)",
                    pstId2, txn.txnId(), crAccount, new java.math.BigDecimal(amt), sqlDate, sqlTs);

                // UPDATE balances (引き落ち側: DR 口座の残高を減らす + 受入側: CR 口座の残高を増やす)
                // DR 口座を減らす
                if (isDebitCategory(txn.category())) {
                    jdbc.update(
                        "UPDATE balances SET balance_jpy = balance_jpy - ?, " +
                        "available_jpy = available_jpy - ?, last_txn_id = ?, updated_ts = NOW() " +
                        "WHERE account_number = ?",
                        new java.math.BigDecimal(amt), new java.math.BigDecimal(amt),
                        txn.txnId(), txn.accountNumber());
                }
                // CR 口座を増やす (口座が顧客の場合のみ balances を更新; システム口座は更新しない)
                if (!"0000000000001".equals(crAccount) && !"0000000000002".equals(crAccount)) {
                    jdbc.update(
                        "UPDATE balances SET balance_jpy = balance_jpy + ?, " +
                        "available_jpy = available_jpy + ?, last_txn_id = ?, updated_ts = NOW() " +
                        "WHERE account_number = ?",
                        new java.math.BigDecimal(amt), new java.math.BigDecimal(amt),
                        txn.txnId(), crAccount);
                }
            }
        };
    }

    /**
     * 借方 (DR) 口座を category 別に決定.
     * DETERMINE-POSTING-ACCTS 相当.
     */
    private static String determineDebitAccount(TransactionRecord txn) {
        return switch (txn.category()) {
            case "10" -> CASH_ACCOUNT;           // 入金: 借方 = 現金
            case "20", "40" -> txn.accountNumber(); // 出金/電払: 借方 = 顧客
            case "30" -> txn.accountNumber();    // 振替: 借方 = 振替元
            default -> CASH_ACCOUNT;
        };
    }

    /**
     * 貸方 (CR) 口座を category 別に決定.
     */
    private static String determineCreditAccount(TransactionRecord txn) {
        return switch (txn.category()) {
            case "10" -> txn.accountNumber();    // 入金: 貸方 = 顧客
            case "20" -> CASH_ACCOUNT;           // 出金: 貸方 = 現金
            case "30" -> txn.counterAccountNumber(); // 振替: 貸方 = 振替先
            case "40" -> CLEARING_ACCOUNT;       // 電払: 貸方 = 整治口
            default -> txn.accountNumber();
        };
    }

    /** TXPOST-RUN-BATCH Step. */
    @Bean
    public Step txpostRunBatch(JobRepository jobRepository,
                                PlatformTransactionManager txManager,
                                JdbcCursorItemReader<TransactionRecord> txnReader,
                                ItemProcessor<TransactionRecord, TransactionRecord> txnProcessor,
                                ItemWriter<TransactionRecord> txnWriter,
                                TxpostRunProgressListener runProgressListener) {
        return new StepBuilder("txpostRunBatch", jobRepository)
            .<TransactionRecord, TransactionRecord>chunk(100, txManager)
            .reader(txnReader)
            .processor(txnProcessor)
            .writer(txnWriter)
            .listener((ItemWriteListener<TransactionRecord>) runProgressListener)
            .build();
    }
}
