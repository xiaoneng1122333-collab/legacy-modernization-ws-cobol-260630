package com.practicebank.batch.txnvalidate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.ItemWriteListener;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * TXVAL-VALIDATE-BATCH 相当の Spring Batch Step.
 *
 * <p>reader → processor → writer の 3 ステージで構成:</p>
 * <ul>
 *   <li>reader: {@code transactions} テーブルからカーソル読み取り</li>
 *   <li>processor: {@link ValidationRules} を適用して ValidationResult を生成</li>
 *   <li>writer: 結果をバッチ更新 (妥当なら status=VO, 拒否なら status=RJ)</li>
 * </ul>
 */
@Configuration
public class TxvalValidateStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TxvalValidateStepConfig.class);

    /** 妥当判定をトランザクションレコードに適用するプロセッサ. */
    @Bean
    public ItemProcessor<TxnTransaction, ValidationResult> validationProcessor() {
        return txn -> {
            List<String> errors = ValidationRules.validate(txn);
            if (errors.isEmpty()) {
                LOG.debug("txn {} validated OK", txn.txnId());
                return ValidationResult.ok(txn.txnId(), txn.sourceBatchId());
            }
            LOG.debug("txn {} rejected: {}", txn.txnId(), errors);
            return ValidationResult.rejected(txn.txnId(), txn.sourceBatchId(), errors);
        };
    }

    /** transactions テーブル (ステータス PT=処理待ち) を SELECT するreader. */
    @Bean
    public JdbcCursorItemReader<TxnTransaction> txnReader(DataSource dataSource,
                                                          @Value("${txnvalidate.batch.id:#{null}}") String batchId) {
        StringBuilder sql = new StringBuilder(
            "SELECT txn_id, business_date, system_ts, category, account_number, " +
            "counter_account_number, amount_jpy, currency, description, source_system, " +
            "source_batch_id, source_seq, status, reversal_of, created_by, created_ts " +
            "FROM transactions WHERE status = 'PT'");
        boolean hasBatchId = (batchId != null && !batchId.isBlank());
        if (hasBatchId) {
            sql.append(" AND source_batch_id = ?");
        }
        sql.append(" ORDER BY source_batch_id, source_seq");
        JdbcCursorItemReaderBuilder<TxnTransaction> builder = new JdbcCursorItemReaderBuilder<TxnTransaction>()
            .name("txnReader")
            .dataSource(dataSource)
            .sql(sql.toString())
            .rowMapper(TxvalValidateStepConfig::mapRow);
        if (hasBatchId) {
            builder.queryArguments(batchId);
        }
        return builder.build();
    }

    private static TxnTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TxnTransaction(
            rs.getString("txn_id"),
            rs.getDate("business_date") != null ? rs.getDate("business_date").toLocalDate() : null,
            rs.getString("category"),
            rs.getString("account_number"),
            rs.getString("counter_account_number"),
            rs.getBigDecimal("amount_jpy"),
            rs.getString("currency"),
            rs.getString("description"),
            rs.getString("source_system"),
            rs.getString("source_batch_id"),
            rs.getInt("source_seq"),
            rs.getString("status"),
            rs.getString("reversal_of"),
            rs.getString("created_by"),
            rs.getTimestamp("created_ts")
        );
    }

    /** バリデーション結果を transactions テーブルにフラグ書き戻すライタ. */
    @Bean
    public JdbcBatchItemWriter<ValidationResult> validationWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<ValidationResult>()
            .dataSource(dataSource)
            .sql("UPDATE transactions SET status = ? WHERE txn_id = ?")
            .itemPreparedStatementSetter((result, ps) -> {
                ps.setString(1, result.valid() ? "VO" : "RJ"); // VO=valid / RJ=rejected
                ps.setString(2, result.transactionId());
            })
            .build();
    }

    /** チェックポイント復元ステップ (TXVAL-CHECKPOINT-RECOVER に相当). */
    @Bean
    public Step recoverCheckpoint(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("recoverCheckpoint", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                // TODO: Phase 2 で実チェックポイントファイル / LAST_SEQ 処理を接続.
                // 今は noop: 全 PT レコードを処理対象とする.
                LOG.info("Checkpoint recovered (noop Phase-2)");
                return org.springframework.batch.repeat.RepeatStatus.FINISHED;
            }, txManager)
            .build();
    }

    /** メインのバリデーション Step. */
    @Bean
    public Step validate(JobRepository jobRepository,
                         PlatformTransactionManager txManager,
                         JdbcCursorItemReader<TxnTransaction> txnReader,
                         ItemProcessor<TxnTransaction, ValidationResult> validationProcessor,
                         JdbcBatchItemWriter<ValidationResult> validationWriter,
                         StepProgressListener stepProgressListener) {
        return new StepBuilder("validate", jobRepository)
            .<TxnTransaction, ValidationResult>chunk(500, txManager)
            .reader(txnReader)
            .processor(validationProcessor)
            .writer(validationWriter)
            .listener((ItemWriteListener<ValidationResult>) stepProgressListener)
            .build();
    }
}
