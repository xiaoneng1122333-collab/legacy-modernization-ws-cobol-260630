package com.practicebank.verify.comparator.model;

import java.util.List;

/**
 * テーブル毎の row count 差分.
 *
 * @param tableName   テーブル名
 * @param cobolCount  COBOL 側スキーマの row count
 * @param javaCount   Java 側スキーマの row count
 * @param diff        javaCount - cobolCount
 * @param status      MATCH / MISMATCH / ONLY_COBOL / ONLY_JAVA
 */
public record DiffEntry(
    String tableName,
    long cobolCount,
    long javaCount,
    long diff,
    String status
) {
    public enum Status {
        MATCH,
        MISMATCH,
        ONLY_COBOL,
        ONLY_JAVA
    }

    public static DiffEntry of(String table, Long cobol, Long javaCount) {
        long c = cobol == null ? 0L : cobol;
        long j = javaCount == null ? 0L : javaCount;
        long delta = j - c;
        String status;
        if (c == 0 && j > 0) status = "ONLY_JAVA";
        else if (c > 0 && j == 0) status = "ONLY_COBOL";
        else if (delta == 0) status = "MATCH";
        else status = "MISMATCH";
        return new DiffEntry(table, c, j, delta, status);
    }
}
