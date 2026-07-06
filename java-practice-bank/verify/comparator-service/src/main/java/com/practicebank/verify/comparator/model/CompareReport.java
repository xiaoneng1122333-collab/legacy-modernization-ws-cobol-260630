package com.practicebank.verify.comparator.model;

import java.time.LocalDate;
import java.util.List;

/**
 * /api/compare/{businessDate} のレスポンス.
 *
 * @param businessDate 比較対象営業日
 * @param tables      テーブルごとの DiffEntry 一覧
 * @param totalDiff   全テーブルの diff 合計
 * @param matchCount
 * @param mismatchCount
 * @param overallStatus 全件一致時 "MATCH", 不一致含み時 "MISMATCH"
 */
public record CompareReport(
    String businessDate,
    List<DiffEntry> tables,
    long totalDiff,
    long matchCount,
    long mismatchCount,
    String overallStatus
) {
    public static CompareReport of(String businessDate, List<DiffEntry> entries) {
        long totalDiff = entries.stream().mapToLong(DiffEntry::diff).sum();
        long matched = entries.stream().filter(e -> "MATCH".equals(e.status())).count();
        long mismatched = entries.size() - matched;
        String overall = mismatched == 0 ? "MATCH" : "MISMATCH";
        return new CompareReport(businessDate, entries, totalDiff, matched, mismatched, overall);
    }
}
