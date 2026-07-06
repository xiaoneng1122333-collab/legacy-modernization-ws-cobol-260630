package com.practicebank.batch.txnsortmerge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * TXSM-MERGE-BATCH 相当の Spring Batch Step.
 *
 * <p>2-way マージ: txn_sorted_txn (sorted) + txn_recon_prev (前日取引).</p>
 * <ul>
 *   <li>RECON がない場合は sorted のみを READY へパススルー.</li>
 *   <li>RECON の (account_number, source_seq) 昇順が崩れている場合は INVALID (08).</li>
 *   <li>同キーが両ストリームに存在する場合は E050 として txn_error_record に退避.</li>
 *   <li>保存量不変条件 (sortedIn + reconIn == mergedOut + duplicateRecords) を検証.</li>
 * </ul>
 */
@Configuration
public class TxsmMergeStepConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TxsmMergeStepConfig.class);
    public static final String CTX_MERGE_OUTPUT = "txsm.merge.output";

    /** ソート済み入力ストリーム. */
    private record SortedRow(String txnId, String accountNumber, int sourceSeq, BigDecimal amountJpy) {
    }

    /** RECON 入力ストリーム. */
    private record ReconRow(String txnId, String accountNumber, int sourceSeq, BigDecimal amountJpy) {
    }

    @Bean
    public Step mergeStep(JobRepository jobRepository,
                          PlatformTransactionManager txManager,
                          DataSource dataSource) {
        return new StepBuilder("merge", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                List<SortedRow> sortedRows = readSorted(dataSource);
                List<ReconRow> reconRows = readRecon(dataSource);

                boolean reconPresent = !reconRows.isEmpty();

                // RECON 順検証 — (account_number, source_seq) 昇順でなければ INVALID.
                int sortViolations = countSortViolations(reconRows);
                if (sortViolations > 0) {
                    TxsmMergeOutput output = new TxsmMergeOutput(
                        TxsmMergeOutput.STATUS_INVALID,
                        sortedRows.size(), reconRows.size(), 0, 0, 0, sortViolations,
                        reconPresent ? "Y" : "N",
                        BigDecimal.ZERO
                    );
                    persistMergeOutput(dataSource, output, sortedRows, reconRows, List.of());
                    chunkContext.getStepContext().getStepExecution()
                        .getJobExecution().getExecutionContext()
                        .put(CTX_MERGE_OUTPUT, output);
                    LOG.info("TXSM-MERGE-OUTPUT: status={} sortViolations={} RECON-SORT-VIOLATION",
                        output.status(), output.sortViolations());
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }

                // 2-way マージ.
                int i = 0;
                int j = 0;
                int mergedOut = 0;
                BigDecimal amountSum = BigDecimal.ZERO;
                List<MergeResult> merged = new ArrayList<>();
                List<MergeResult> duplicates = new ArrayList<>();

                while (i < sortedRows.size() && j < reconRows.size()) {
                    SortedRow s = sortedRows.get(i);
                    ReconRow r = reconRows.get(j);
                    int cmp = compare(s.accountNumber(), s.sourceSeq(), r.accountNumber(), r.sourceSeq());
                    if (cmp < 0) {
                        merged.add(new MergeResult("SORTED", s.txnId(), s.accountNumber(), s.sourceSeq(), s.amountJpy(), false));
                        amountSum = amountSum.add(s.amountJpy());
                        i++;
                    } else if (cmp > 0) {
                        merged.add(new MergeResult("RECON", r.txnId(), r.accountNumber(), r.sourceSeq(), r.amountJpy(), false));
                        amountSum = amountSum.add(r.amountJpy());
                        j++;
                    } else {
                        // 重複 — E050.
                        duplicates.add(new MergeResult("SORTED", s.txnId(), s.accountNumber(), s.sourceSeq(), s.amountJpy(), true));
                        duplicates.add(new MergeResult("RECON", r.txnId(), r.accountNumber(), r.sourceSeq(), r.amountJpy(), true));
                        i++;
                        j++;
                    }
                }
                while (i < sortedRows.size()) {
                    SortedRow s = sortedRows.get(i++);
                    merged.add(new MergeResult("SORTED", s.txnId(), s.accountNumber(), s.sourceSeq(), s.amountJpy(), false));
                    amountSum = amountSum.add(s.amountJpy());
                }
                while (j < reconRows.size()) {
                    ReconRow r = reconRows.get(j++);
                    merged.add(new MergeResult("RECON", r.txnId(), r.accountNumber(), r.sourceSeq(), r.amountJpy(), false));
                    amountSum = amountSum.add(r.amountJpy());
                }

                int dupRecords = duplicates.size();
                int dupPairs = dupRecords / 2;
                mergedOut = merged.size();

                // 保存量不変条件: sortedIn + reconIn == mergedOut + duplicate-records.
                int expected = mergedOut + dupRecords;
                int actual = sortedRows.size() + reconRows.size();
                boolean conservationOk = (expected == actual);

                String status;
                if (!conservationOk) {
                    status = TxsmMergeOutput.STATUS_INVALID;
                } else if (dupRecords > 0) {
                    status = TxsmMergeOutput.STATUS_PARTIAL;
                } else {
                    status = TxsmMergeOutput.STATUS_OK;
                }

                // persist ready + error records.
                persistMergedReady(dataSource, merged);
                persistErrorRecords(dataSource, duplicates);

                // SE → MG (merged) に更新.
                var updConn = dataSource.getConnection();
                try (var ps = updConn.prepareStatement(
                    "UPDATE transactions SET status = 'MG' WHERE status = 'SE'")) {
                    ps.executeUpdate();
                } finally {
                    updConn.close();
                }

                TxsmMergeOutput output = new TxsmMergeOutput(
                    status,
                    sortedRows.size(),
                    reconRows.size(),
                    mergedOut,
                    dupRecords,
                    dupPairs,
                    sortViolations,
                    reconPresent ? "Y" : "N",
                    amountSum
                );
                persistMergeOutput(dataSource, output, sortedRows, reconRows, duplicates);
                chunkContext.getStepContext().getStepExecution()
                    .getJobExecution().getExecutionContext()
                    .put(CTX_MERGE_OUTPUT, output);

                LOG.info("TXSM-MERGE-OUTPUT: status={} sin={} rin={} out={} dup={} pairs={} viol={} recon={} sum={}",
                    output.status(), output.recordsSortedIn(), output.recordsReconIn(),
                    output.recordsMergedOut(), output.duplicateRecords(), output.duplicatePairs(),
                    output.sortViolations(), output.reconPresentFlag(), output.amountSum());
                return org.springframework.batch.repeat.RepeatStatus.FINISHED;
            }, txManager)
            .build();
    }

    private List<SortedRow> readSorted(DataSource dataSource) {
        List<SortedRow> rows = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                 "SELECT txn_id, account_number, source_seq, amount_jpy FROM txn_sorted_txn ORDER BY sorted_seq");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new SortedRow(
                    rs.getString("txn_id"),
                    rs.getString("account_number"),
                    rs.getInt("source_seq"),
                    rs.getBigDecimal("amount_jpy")
                ));
            }
        } catch (Exception e) {
            LOG.error("readSorted failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return rows;
    }

    private List<ReconRow> readRecon(DataSource dataSource) {
        List<ReconRow> rows = new ArrayList<>();
        try (var conn = dataSource.getConnection()) {
            // RECON テーブルが未作成の場合は空とみなす (RECON なしパススルー).
            try (var ps = conn.prepareStatement(
                "SELECT txn_id, account_number, source_seq, amount_jpy FROM txn_recon_prev ORDER BY account_number, source_seq");
                 var rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ReconRow(
                        rs.getString("txn_id"),
                        rs.getString("account_number"),
                        rs.getInt("source_seq"),
                        rs.getBigDecimal("amount_jpy")
                    ));
                }
            } catch (Exception e) {
                // recon table absent → treat as no recon.
                LOG.info("txn_recon_prev not found (treated as no recon)");
                rows.clear();
            }
        } catch (Exception e) {
            LOG.error("readRecon failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return rows;
    }

    private int countSortViolations(List<ReconRow> rows) {
        int violations = 0;
        for (int k = 1; k < rows.size(); k++) {
            ReconRow prev = rows.get(k - 1);
            ReconRow cur = rows.get(k);
            if (compare(prev.accountNumber(), prev.sourceSeq(), cur.accountNumber(), cur.sourceSeq()) > 0) {
                violations++;
            }
        }
        return violations;
    }

    private int compare(String acctA, int seqA, String acctB, int seqB) {
        int c = acctA.compareTo(acctB);
        if (c != 0) return c;
        return Integer.compare(seqA, seqB);
    }

    private void persistMergeOutput(DataSource ds, TxsmMergeOutput output,
                                    List<SortedRow> sorted, List<ReconRow> recon,
                                    List<MergeResult> duplicates) {
        // output is stored in execution context — nothing else to persist here (future use: audit table).
    }

    private void persistMergedReady(DataSource ds, List<MergeResult> merged) {
        try (var conn = ds.getConnection()) {
            try (var trunc = conn.createStatement()) {
                trunc.execute("TRUNCATE txn_ready_txn");
            }
            try (var ps = conn.prepareStatement(
                "INSERT INTO txn_ready_txn (txn_id_source, source_kind, account_number, source_seq, amount_jpy) " +
                "VALUES (?, ?, ?, ?, ?)")) {
                for (MergeResult r : merged) {
                    ps.setString(1, r.txnId());
                    ps.setString(2, r.source());
                    ps.setString(3, r.accountNumber());
                    ps.setInt(4, r.sourceSeq());
                    ps.setBigDecimal(5, r.amountJpy());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (Exception e) {
            LOG.error("persistMergedReady failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void persistErrorRecords(DataSource ds, List<MergeResult> duplicates) {
        try (var conn = ds.getConnection()) {
            try (var trunc = conn.createStatement()) {
                trunc.execute("TRUNCATE txn_error_record");
            }
            try (var ps = conn.prepareStatement(
                "INSERT INTO txn_error_record (txn_id, account_number, source_seq, amount_jpy, error_code, source_kind) " +
                "VALUES (?, ?, ?, ?, 'E050', ?)")) {
                for (MergeResult r : duplicates) {
                    ps.setString(1, r.txnId());
                    ps.setString(2, r.accountNumber());
                    ps.setInt(3, r.sourceSeq());
                    ps.setBigDecimal(4, r.amountJpy());
                    ps.setString(5, r.source());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (Exception e) {
            LOG.error("persistErrorRecords failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
