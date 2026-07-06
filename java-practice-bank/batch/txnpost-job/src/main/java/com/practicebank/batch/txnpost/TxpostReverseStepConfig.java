package com.practicebank.batch.txnpost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TXPOST-REVERSE 相当の Spring Batch Step.
 *
 * <p>orig-txn-id で transactions 表を引き、ステータスが PT/SE の伝票に対して逆伝票を生成.
 * tasklet 実装で単一トランザクション (SERIALIZABLE) を実行.
 *
 * <p>入力パラメータ (JobParameters 渡し):
 * <ul>
 *   <li>orig-txn-id — 逆伝票の元取引 ID (transactions.txn_id)</li>
 *   <li>reversal-reason — 取消事由</li>
 *   <li>operator-id — 操作者 ID</li>
 * </ul>
 */
@Configuration
public class TxpostReverseStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TxpostReverseStepConfig.class);

    /** システム口座 (現金・整治口) — 逆伝票の DR/CR 逆転先. */
    private static final String CASH_ACCOUNT = "0000000000001";
    private static final String CLEARING_ACCOUNT = "0000000000002";

    @Value("${txpost.business.date:#{null}}")
    private String businessDate;

    @Bean
    public Step txpostReverse(JobRepository jobRepository,
                               PlatformTransactionManager txManager,
                               DataSource dataSource) {
        return new StepBuilder("txpostReverse", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);

                // ジョブパラメータから入力取得
                String origTxnId = chunkContext.getStepContext().getJobParameters()
                    .getOrDefault("orig.txn.id", "").toString();
                String reason = chunkContext.getStepContext().getJobParameters()
                    .getOrDefault("reversal.reason", "REVERSAL").toString();
                String operatorId = chunkContext.getStepContext().getJobParameters()
                    .getOrDefault("operator.id", "SYSTEM").toString();

                // VALIDATE-INPUT: 3 必須項目
                if (origTxnId == null || origTxnId.isBlank()) {
                    LOG.warn("TXPOST-REVERSE: orig-txn-id is blank, skip");
                    return RepeatStatus.FINISHED;
                }

                // SELECT-ORIG-TXN
                TransactionRecord orig;
                try {
                    orig = jdbc.queryForObject(
                        "SELECT txn_id, business_date, system_ts, category, account_number, " +
                        "counter_account_number, amount_jpy, currency, description, source_system, " +
                        "source_batch_id, source_seq, status, reversal_of, created_by, created_ts " +
                        "FROM transactions WHERE txn_id = ?",
                        (rs, rowNum) -> new TransactionRecord(
                            rs.getString("txn_id"),
                            rs.getDate("business_date") != null ? rs.getDate("business_date").toLocalDate() : null,
                            rs.getTimestamp("system_ts") != null ? rs.getTimestamp("system_ts").toLocalDateTime() : null,
                            rs.getString("category"),
                            rs.getString("account_number"),
                            rs.getString("counter_account_number"),
                            rs.getBigDecimal("amount_jpy") != null ? rs.getBigDecimal("amount_jpy").toBigInteger() : java.math.BigInteger.ZERO,
                            rs.getString("currency"),
                            rs.getString("description"),
                            rs.getString("source_system"),
                            rs.getString("source_batch_id"),
                            rs.getInt("source_seq"),
                            rs.getString("status"),
                            rs.getString("reversal_of"),
                            rs.getString("created_by"),
                            rs.getTimestamp("created_ts") != null ? rs.getTimestamp("created_ts").toLocalDateTime() : null
                        ),
                        origTxnId.trim());
                } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                    LOG.warn("TXPOST-REVERSE: orig-txn-id={} not found", origTxnId);
                    return RepeatStatus.FINISHED;
                }

                // CHECK-ORIG-STATUS: PT/SE のみ可
                if (orig == null || (!"PT".equals(orig.status()) && !"SE".equals(orig.status()))) {
                    LOG.warn("TXPOST-REVERSE: txn {} status={} not reversible", origTxnId,
                        orig != null ? orig.status() : "null");
                    return RepeatStatus.FINISHED;
                }

                // CHECK-ALREADY-REVERSED
                Integer existingRv = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE reversal_of = ? AND status = 'RV'",
                    Integer.class, origTxnId.trim());
                if (existingRv != null && existingRv > 0) {
                    LOG.warn("TXPOST-REVERSE: txn {} already reversed", origTxnId);
                    return RepeatStatus.FINISHED;
                }

                // DETERMINE-RV-ACCTS: DR/CR 逆転
                String drAccount = determineRvDebitAccount(orig);
                String crAccount = determineRvCreditAccount(orig);

                // INSERT-RV-TXN
                String rvTxnId = "TXN-RV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                LocalDateTime now = LocalDateTime.now();
                jdbc.update(
                    "INSERT INTO transactions (txn_id, business_date, system_ts, category, " +
                    "account_number, counter_account_number, amount_jpy, currency, description, " +
                    "source_system, source_batch_id, source_seq, status, reversal_of, created_by, created_ts) " +
                    "VALUES (?, ?, ?, 'RV', ?, ?, ?, 'JPY', ?, 'REV', ?, 0, 'RV', ?, ?, ?)",
                    rvTxnId,
                    orig.businessDate() != null ? java.sql.Date.valueOf(orig.businessDate()) : java.sql.Date.valueOf(java.time.LocalDate.now()),
                    Timestamp.valueOf(now),
                    crAccount,
                    drAccount,
                    orig.amountJpy(),
                    "REVERSAL: " + reason,
                    orig.sourceBatchId() != null ? orig.sourceBatchId() : "REVERSE",
                    origTxnId.trim(),
                    operatorId,
                    Timestamp.valueOf(now)
                );

                // INSERT-RV-POSTINGS (2 行: DR + CR 逆転)
                LocalDateTime createdTs = LocalDateTime.now();
                jdbc.update(
                    "INSERT INTO postings (posting_id, txn_id, line_no, account_number, " +
                    "debit_jpy, credit_jpy, posting_role, business_date, created_ts) " +
                    "VALUES (?, ?, 1, ?, ?, 0, 'DR', ?, ?)",
                    "PST-RV-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
                    rvTxnId,
                    drAccount,
                    orig.amountJpy(),
                    orig.businessDate() != null ? java.sql.Date.valueOf(orig.businessDate()) : java.sql.Date.valueOf(java.time.LocalDate.now()),
                    Timestamp.valueOf(createdTs)
                );
                jdbc.update(
                    "INSERT INTO postings (posting_id, txn_id, line_no, account_number, " +
                    "debit_jpy, credit_jpy, posting_role, business_date, created_ts) " +
                    "VALUES (?, ?, 2, ?, 0, ?, 'CR', ?, ?)",
                    "PST-RV-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
                    rvTxnId,
                    crAccount,
                    orig.amountJpy(),
                    orig.businessDate() != null ? java.sql.Date.valueOf(orig.businessDate()) : java.sql.Date.valueOf(java.time.LocalDate.now()),
                    Timestamp.valueOf(createdTs)
                );

                // UPDATE-RV-BALANCES (2 口座). 逆伝票では元の DR→CR 逆転.
                jdbc.update(
                    "UPDATE balances SET balance_jpy = balance_jpy + ?, " +
                    "available_jpy = available_jpy + ?, last_txn_id = ?, updated_ts = NOW() " +
                    "WHERE account_number = ?",
                    orig.amountJpy(), orig.amountJpy(), rvTxnId, crAccount
                );
                jdbc.update(
                    "UPDATE balances SET balance_jpy = balance_jpy - ?, " +
                    "available_jpy = available_jpy - ?, last_txn_id = ?, updated_ts = NOW() " +
                    "WHERE account_number = ?",
                    orig.amountJpy(), orig.amountJpy(), rvTxnId, drAccount
                );

                // ORIG 取引の status を RV (reversed) に更新 (RV status is already reserved).
                // schema では status IN ('PT','SE,'RV') — orig は直前の状態。reversal_of に RV を入れるので
                // orig の status は変更しない (COBOL でも PT/SE のまま履歴保持).

                // JobExecutionContext に結果を設定
                chunkContext.getStepContext().getStepExecution().getJobExecution()
                    .getExecutionContext().put("txpost.rvTxnId", rvTxnId);
                chunkContext.getStepContext().getStepExecution().getJobExecution()
                    .getExecutionContext().put("txpost.reverseStatus", "TXPV-OK");

                LOG.info("TXPOST-REVERSE: orig={} reversed by rvTxnId={}", origTxnId, rvTxnId);
                return RepeatStatus.FINISHED;
            }, txManager)
            .build();
    }

    /**
     * 逆伝票 借方 (DR) 口座 — 元取引の CR 口座を借方に逆転.
     */
    private static String determineRvDebitAccount(TransactionRecord orig) {
        // 逆伝票: 元の CR 側を DR に
        return switch (orig.category()) {
            case "10" -> orig.accountNumber();     // 入金の逆: DR = 顧客 (戻し)
            case "20" -> CASH_ACCOUNT;              // 出金の逆: DR = 現金
            case "30" -> orig.counterAccountNumber(); // 振替の逆: DR = 振替先
            case "40" -> orig.accountNumber();      // 電払の逆: DR = 顧客
            default -> orig.accountNumber();
        };
    }

    /**
     * 逆伝票 貸方 (CR) 口座 — 元取引の DR 口座を貸方に逆転.
     */
    private static String determineRvCreditAccount(TransactionRecord orig) {
        // 逆伝票: 元の DR 側を CR に
        return switch (orig.category()) {
            case "10" -> CASH_ACCOUNT;              // 入金の逆: CR = 現金
            case "20" -> orig.accountNumber();      // 出金の逆: CR = 顧客
            case "30" -> orig.accountNumber();       // 振替の逆: CR = 振替元
            case "40" -> CLEARING_ACCOUNT;          // 電払の逆: CR = 整治口
            default -> orig.accountNumber();
        };
    }
}
