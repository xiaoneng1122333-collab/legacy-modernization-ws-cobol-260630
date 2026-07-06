package com.practicebank.batch.txnsortmerge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * TXSM-SORT-BATCH 相当の Spring Batch Step.
 *
 * <p>reader → in-memory sort → writer の 3 ステージで構成:</p>
 * <ul>
 *   <li>reader: {@code transactions} テーブルから status='VO' (妥当性済) を SELECT (ORDER BY は掛けず、Java 側で安定ソート)</li>
 *   <li>processor: {@code SortedTransaction} に変換し sortedSeq を付番 (in-memory sort)</li>
 *   <li>writer: {@code txn_sorted_txn} テーブルに INSERT (Sorter の OUTPUT PROCEDURE 相当)</li>
 * </ul>
 *
 * <p>ロスレス (in == out) と金額合計チェックは Step 終了時に {@link ExecutionContext} 経由で上位に通知.</p>
 */
@Configuration
public class TxsmSortStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TxsmSortStepConfig.class);
    public static final String CTX_SORT_OUTPUT = "txsm.sort.output";

    /**
     * 全件一括でソートする Tasklet 方式. Spring Batch の chunk 方式が不得意な
     * 「全件ソート→一括書き出し」パターンでは Tasklet が適切.
     */
    @Bean
    public Step sortStepTasklet(JobRepository jobRepository,
                                PlatformTransactionManager txManager,
                                DataSource dataSource,
                                @Value("${txnsortmerge.batch.id:#{null}}") String batchId) {
        return new StepBuilder("sort", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                StringBuilder sql = new StringBuilder(
                    "SELECT txn_id, account_number, source_seq, amount_jpy " +
                    "FROM transactions WHERE status = 'VO'");
                boolean hasBatchId = (batchId != null && !batchId.isBlank());
                if (hasBatchId) {
                    sql.append(" AND source_batch_id = ?");
                }
                sql.append(" ORDER BY account_number ASC, source_seq ASC");

                List<TxnSortInputRow> rows = new ArrayList<>();
                var conn = dataSource.getConnection();
                java.sql.PreparedStatement ps;
                try {
                    ps = conn.prepareStatement(sql.toString());
                    if (hasBatchId) {
                        ps.setString(1, batchId);
                    }
                    var rs = ps.executeQuery();
                    while (rs.next()) {
                        rows.add(new TxnSortInputRow(
                            rs.getString("txn_id"),
                            rs.getString("account_number"),
                            rs.getInt("source_seq"),
                            rs.getBigDecimal("amount_jpy")
                        ));
                    }
                    rs.close();
                    ps.close();
                } finally {
                    conn.close();
                }

                // 念のため Java 側でも安定ソート (payer-acct ASC, seq ASC)
                rows.sort(Comparator
                    .comparing(TxnSortInputRow::accountNumber)
                    .thenComparingInt(TxnSortInputRow::sourceSeq));

                int recordsIn = rows.size();
                BigDecimal amountSum = rows.stream()
                    .map(TxnSortInputRow::amountJpy)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 出力
                var outConn = dataSource.getConnection();
                try {
                    // truncate-and-load
                    try (var trunc = outConn.createStatement()) {
                        trunc.execute("TRUNCATE txn_sorted_txn");
                    }
                    try (var psInsert = outConn.prepareStatement(
                        "INSERT INTO txn_sorted_txn (txn_id, account_number, source_seq, amount_jpy, sorted_seq) " +
                        "VALUES (?, ?, ?, ?, ?)")) {
                        long seq = 1L;
                        for (TxnSortInputRow r : rows) {
                            psInsert.setString(1, r.txnId());
                            psInsert.setString(2, r.accountNumber());
                            psInsert.setInt(3, r.sourceSeq());
                            psInsert.setBigDecimal(4, r.amountJpy());
                            psInsert.setLong(5, seq++);
                            psInsert.addBatch();
                        }
                        psInsert.executeBatch();
                    }
                } finally {
                    outConn.close();
                }

                // ロスレス検証 (in == out)
                String ctrlMatch = (recordsIn == rows.size()) ? "Y" : "N";
                String status = (recordsIn == rows.size()) ? TxsmSortOutput.STATUS_OK : TxsmSortOutput.STATUS_INVALID;

                // 入力ステータス更新: VO → SE (sorted)
                var updConn = dataSource.getConnection();
                try (var psUpdate = updConn.prepareStatement(
                    "UPDATE transactions SET status = 'SE' WHERE status = 'VO'" +
                    (hasBatchId ? " AND source_batch_id = ?" : ""))) {
                    if (hasBatchId) {
                        psUpdate.setString(1, batchId);
                    }
                    psUpdate.executeUpdate();
                } finally {
                    updConn.close();
                }

                TxsmSortOutput output = new TxsmSortOutput(status, recordsIn, recordsIn, ctrlMatch, amountSum);
                chunkContext.getStepContext().getStepExecution()
                    .getJobExecution().getExecutionContext()
                    .put(CTX_SORT_OUTPUT, output);

                LOG.info("TXSM-SORT-OUTPUT: status={} recordsProcessed={} recordsSorted={} ctrlMatch={} amountSum={}",
                    output.status(), output.recordsProcessed(), output.recordsSorted(),
                    output.ctrlTotalMatch(), output.amountSum());
                return org.springframework.batch.repeat.RepeatStatus.FINISHED;
            }, txManager)
            .build();
    }

    /**
     * ソート対象行の中間 DTO.
     */
    public record TxnSortInputRow(String txnId, String accountNumber, int sourceSeq, BigDecimal amountJpy) {
    }
}
