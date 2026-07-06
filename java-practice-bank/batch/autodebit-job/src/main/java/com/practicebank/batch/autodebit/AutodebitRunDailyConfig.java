package com.practicebank.batch.autodebit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
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
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AD-RUN-DAILY 相当の Spring Batch Step.
 *
 * <p>reader → processor → writer の 3 ステージで構成:</p>
 * <ul>
 *   <li>reader: {@code autodebit_schedules} から status='AC' かつ next_due_date <= 業務日 をカーソル取得</li>
 *   <li>processor: ACCT-EXISTS → 残高取得 → double-entry-helper → POST-PAIR 相当の検証</li>
 *   <li>writer: 結果をバッチ更新 (POST 成功時は balances 減算 + 次回期日更新, 失敗時は fails++ / ステータス遷移)</li>
 * </ul>
 */
@Configuration
public class AutodebitRunDailyConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AutodebitRunDailyConfig.class);

    /** 自動引き落とし指令を 1 件処理するプロセッサ. */
    @Bean
    public ItemProcessor<AutodebitInstruction, AutodebitPostResult> autodebitProcessor(
            DataSource dataSource,
            @Value("${autodebit.batch.id:#{null}}") String batchId,
            @Value("${autodebit.business.date:#{null}}") String businessDate) {
        return instruction -> {
            List<String> errors = new ArrayList<>();

            // ACCT-EXISTS: 口座存在・状態検証 (balances テーブルに口座があるか)
            Long balance = fetchBalance(dataSource, instruction.payerAccount());
            if (balance == null) {
                errors.add("CL"); // 口座異常 (口座未発見)
                return AutodebitPostResult.failed(instruction.instructionId(), batchId,
                    instruction.amountJpy(), "CL", errors);
            }

            // 残高不足チェック (NSF 判定)
            if (balance < instruction.amountJpy()) {
                errors.add("NF"); // 残高不足
                return AutodebitPostResult.failed(instruction.instructionId(), batchId,
                    instruction.amountJpy(), "NF", errors);
            }

            // double-entry-helper 相当: 金額・通貨の整合性検証
            if (instruction.amountJpy() == null || instruction.amountJpy() <= 0) {
                errors.add("HE"); // helper 検証拒否
                return AutodebitPostResult.failed(instruction.instructionId(), batchId,
                    instruction.amountJpy(), "HE", errors);
            }

            LOG.debug("instruction {} validated OK, balance={}, amount={}",
                instruction.instructionId(), balance, instruction.amountJpy());
            return AutodebitPostResult.ok(instruction.instructionId(), batchId, instruction.amountJpy());
        };
    }

    private Long fetchBalance(DataSource dataSource, String accountNumber) {
        try {
            return new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .queryForObject("SELECT balance_jpy FROM balances WHERE account_number = ?",
                    Long.class, accountNumber);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** autodebit_schedules テーブル (ステータス AC かつ next_due_date <= 業務日) を SELECT するreader. */
    @Bean
    @StepScope
    public JdbcCursorItemReader<AutodebitInstruction> autodebitReader(DataSource dataSource,
                                                                       @Value("${autodebit.business.date:#{null}}") String businessDate) {
        StringBuilder sql = new StringBuilder(
            "SELECT instruction_id, payer_account, payee_name, amount_jpy, frequency, " +
            "next_due_date, status, last_attempt_date, last_attempt_result, consecutive_failures " +
            "FROM autodebit_schedules WHERE status = 'AC'");
        boolean hasBusinessDate = (businessDate != null && !businessDate.isBlank());
        if (hasBusinessDate) {
            sql.append(" AND next_due_date <= CAST(? AS DATE)");
        }
        sql.append(" ORDER BY instruction_id LIMIT 500");

        JdbcCursorItemReaderBuilder<AutodebitInstruction> builder = new JdbcCursorItemReaderBuilder<AutodebitInstruction>()
            .name("autodebitReader")
            .dataSource(dataSource)
            .sql(sql.toString())
            .rowMapper(AutodebitRunDailyConfig::mapRow);
        if (hasBusinessDate) {
            builder.queryArguments(businessDate);
        }
        return builder.build();
    }

    private static AutodebitInstruction mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Date nextDue = rs.getDate("next_due_date");
        java.sql.Date lastAttempt = rs.getDate("last_attempt_date");
        return new AutodebitInstruction(
            rs.getString("instruction_id"),
            rs.getString("payer_account"),
            rs.getString("payee_name"),
            rs.getLong("amount_jpy"),
            rs.getString("frequency"),
            nextDue != null ? nextDue.toLocalDate() : null,
            rs.getString("status"),
            lastAttempt != null ? lastAttempt.toLocalDate() : null,
            rs.getString("last_attempt_result"),
            rs.getInt("consecutive_failures")
        );
    }

    /** POST 結果を autodebit_schedules テーブルに反映するライタ. */
    @Bean
    public JdbcBatchItemWriter<AutodebitPostResult> autodebitWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<AutodebitPostResult>()
            .dataSource(dataSource)
            .sql("UPDATE autodebit_schedules SET " +
                "status = CASE " +
                "  WHEN ? = 'TM' THEN 'TM' " + // CL → TM (自動終了)
                "  WHEN consecutive_failures + 1 >= 3 THEN 'SP' " + // 連続 3 回 → 休止
                "  WHEN ? = 'OK' THEN 'AC' " +
                "  ELSE 'AC' END, " +
                "consecutive_failures = CASE " +
                "  WHEN ? = 'OK' THEN 0 " +
                "  ELSE consecutive_failures + 1 END, " +
                "last_attempt_date = CURRENT_DATE, last_attempt_result = ?, updated_ts = NOW() " +
                "WHERE instruction_id = ?")
            .itemPreparedStatementSetter((result, ps) -> {
                String resultCd = result.posted() ? "OK" : result.reason();
                // 口座異常 (CL) → 強制的に TM に遷移
                String errorOverride = result.errors().contains("CL") ? "TM" : resultCd;
                ps.setString(1, errorOverride); // TM override
                ps.setString(2, resultCd);      // OK → AC, それ以外 → AC/SP
                ps.setString(3, resultCd);      // OK → reset fails
                ps.setString(4, resultCd);      // last_attempt_result
                ps.setString(5, result.instructionId());
            })
            .build();
    }

    /** AD-RUN-DAILY メイン Step. */
    @Bean
    public Step runDaily(JobRepository jobRepository,
                         PlatformTransactionManager txManager,
                         JdbcCursorItemReader<AutodebitInstruction> autodebitReader,
                         ItemProcessor<AutodebitInstruction, AutodebitPostResult> autodebitProcessor,
                         JdbcBatchItemWriter<AutodebitPostResult> autodebitWriter,
                         AutodebitStepProgressListener stepProgressListener) {
        return new StepBuilder("runDaily", jobRepository)
            .<AutodebitInstruction, AutodebitPostResult>chunk(500, txManager)
            .reader(autodebitReader)
            .processor(autodebitProcessor)
            .writer(autodebitWriter)
            .listener((ItemWriteListener<AutodebitPostResult>) stepProgressListener)
            .build();
    }
}
