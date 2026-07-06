package com.practicebank.batch.interestaccrual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Step;
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
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * IACR-RUN-DAILY 相当の Spring Batch Step.
 *
 * <p>reader → processor → writer の 3 ステージで構成:</p>
 * <ul>
 *   <li>reader: {@code balances} テーブルからカーソル読み取り</li>
 *   <li>processor: ACCT-EXISTS / PROD-LOOKUP / IRATE-LOOKUP 相当の利息適格性判定と利息算出</li>
 *   <li>writer: 結果を interest_accruals テーブルに INSERT (idempotent)</li>
 * </ul>
 */
@Configuration
public class IacrRunDailyStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(IacrRunDailyStepConfig.class);

    /**
     * COBOL 88 値と同じシステム口座ブラックリスト (IACR-RUN-DAILY の SYS_SKIP 相当).
     * 運用口座・仮口座など利息計算対象外.
     */
    private static final java.util.Set<String> SYSTEM_BLACKLIST = java.util.Set.of(
        "0000000000000", "0000000000001", "0000000000002", "0000000000003"
    );

    /** 利息算出の年日数 (日割り計算の分母). */
    private static final int DAYS_IN_YEAR = 365;

    @Value("${iacr.business.date:#{null}}")
    private String businessDate;

    /** balances テーブルを SELECT する reader. */
    @Bean
    public JdbcCursorItemReader<BalanceRecord> balanceReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<BalanceRecord>()
            .name("balanceReader")
            .dataSource(dataSource)
            .sql("SELECT account_number, balance_jpy, available_jpy, hold_jpy, " +
                 "last_txn_id, last_business_date, updated_ts FROM balances ORDER BY account_number")
            .rowMapper(IacrRunDailyStepConfig::mapRow)
            .build();
    }

    private static BalanceRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new BalanceRecord(
            rs.getString("account_number"),
            rs.getLong("balance_jpy"),
            rs.getLong("available_jpy"),
            rs.getLong("hold_jpy"),
            rs.getString("last_txn_id"),
            rs.getDate("last_business_date") != null ? rs.getDate("last_business_date").toLocalDate() : null,
            rs.getTimestamp("updated_ts")
        );
    }

    /**
     * 利息適格性判定と利息算出プロセッサ.
     * COBOL 設計の SYS_SKIP / ACCT-EXISTS / PROD-LOOKUP / IRATE-LOOKUP 判定を Java で表現.
     * <p>
     * Phase 2 ではマスタ参照 (CAL-LOOKUP / BR-LOOKUP / PROD-LOOKUP) を必要とするルールは
     * 別途マスタ整備後に有効化. 当面はシンプルなロジックで表現.
     *
     * @return 適格なら AccrualRecord、不適格 (スキップ) なら null.
     */
    @Bean
    public ItemProcessor<BalanceRecord, AccrualRecord> accrualProcessor() {
        return balance -> {
            // SYS_SKIP — システム口座ブラックリスト
            if (balance.accountNumber() != null && SYSTEM_BLACKLIST.contains(balance.accountNumber().trim())) {
                LOG.debug("account {} system blacklisted, skip", balance.accountNumber());
                return null;
            }

            // ACCT-EXISTS / PROD-LOOKUP 相当 — 残高 0 以下はスキップ (INELIGIBLE-BALANCE)
            if (balance.balanceJpy() == null || balance.balanceJpy() <= 0) {
                LOG.debug("account {} balance <= 0, skip", balance.accountNumber());
                return null;
            }

            // IRATE-LOOKUP 相当 — 金利取得 (簡易固定金利 0.05% を Phase 2 で実装)
            // Phase 2 マスタ整備時に IR-LOOKUP テーブル参照に差し替え.
            BigDecimal rate = new BigDecimal("0.0005");

            // 利息算出: principal * rate / 365 (日切)
            BigDecimal principal = BigDecimal.valueOf(balance.balanceJpy());
            BigDecimal accrued = principal.multiply(rate)
                .divide(BigDecimal.valueOf(DAYS_IN_YEAR), 0, RoundingMode.DOWN);

            LOG.debug("account {} accrued={} principal={} rate={}",
                balance.accountNumber(), accrued, principal, rate);

            return new AccrualRecord(
                businessDate != null ? businessDate : "20260101",
                balance.accountNumber() != null ? balance.accountNumber().trim() : "0000000000000",
                "SA1",  // SAVINGS product code (Phase2 PROD-LOOKUP 相当)
                balance.balanceJpy(),
                rate,
                (short) 1,
                accrued.longValue(),
                "PT"  // pending -> 経過で AC 化 (Phase2 で確定処理)
            );
        };
    }

    /** YYYYMMDD 文字列を {@link java.sql.Date} に変換. */
    private static Date parseBusinessDate(String bdate) {
        if (bdate == null || bdate.isBlank()) {
            return null;
        }
        LocalDate localDate = LocalDate.parse(bdate.trim(), DateTimeFormatter.BASIC_ISO_DATE);
        return Date.valueOf(localDate);
    }

    /** 利息計算結果を interest_accruals テーブルに INSERT する writer (idempotent). */
    @Bean
    public JdbcBatchItemWriter<AccrualRecord> accrualWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<AccrualRecord>()
            .dataSource(dataSource)
            .sql("INSERT INTO interest_accruals " +
                 "(business_date, account_number, product_code, principal_jpy, rate, days, accrued_jpy, status) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT (business_date, account_number) DO NOTHING")
            .itemPreparedStatementSetter((record, ps) -> {
                ps.setDate(1, parseBusinessDate(record.businessDate()));
                ps.setString(2, record.accountNumber());
                ps.setString(3, record.productCode());
                ps.setLong(4, record.principalJpy());
                ps.setBigDecimal(5, record.rate());
                ps.setShort(6, record.days());
                ps.setLong(7, record.accruedJpy());
                ps.setString(8, record.status());
            })
            // ON CONFLICT DO NOTHING では競合時に update count 0 が返るため assertion を無効化.
            .assertUpdates(false)
            .build();
    }

    /** IACR-RUN-DAILY Step. */
    @Bean
    public Step iacrRunDaily(JobRepository jobRepository,
                             PlatformTransactionManager txManager,
                             JdbcCursorItemReader<BalanceRecord> balanceReader,
                             ItemProcessor<BalanceRecord, AccrualRecord> accrualProcessor,
                             JdbcBatchItemWriter<AccrualRecord> accrualWriter,
                             IacrRunProgressListener runProgressListener) {
        return new StepBuilder("iacrRunDaily", jobRepository)
            .<BalanceRecord, AccrualRecord>chunk(500, txManager)
            .reader(balanceReader)
            .processor(accrualProcessor)
            .writer(accrualWriter)
            .listener((ItemWriteListener<AccrualRecord>) runProgressListener)
            .build();
    }
}
