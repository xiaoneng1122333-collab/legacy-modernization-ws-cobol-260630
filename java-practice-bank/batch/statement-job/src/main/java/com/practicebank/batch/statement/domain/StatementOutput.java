package com.practicebank.batch.statement.domain;

/**
 * STMT-OUTPUT 相当 — STMT-GENERATE-BATCH の返却コード・集計フィールド.
 *
 * <p>COBOL 88 レベル値を保持 (00=OK, 04=PARTIAL, 08=INVALID-INPUT, 12=IO-FAIL, 16=FATAL).</p>
 */
public record StatementOutput(
    String status,
    int accountsProcessed,
    int accountsEmpty,
    int accountsSkipped,
    int linesWritten,
    int pagesWritten,
    long bytesWritten,
    long durationSec
) {
    public static final String OK = "00";
    public static final String PARTIAL = "04";
    public static final String INVALID_INPUT = "08";
    public static final String IO_FAIL = "12";
    public static final String FATAL = "16";

    public boolean isOk() { return OK.equals(status); }
}
