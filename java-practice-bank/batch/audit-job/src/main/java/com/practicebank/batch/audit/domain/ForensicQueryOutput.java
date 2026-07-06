package com.practicebank.batch.audit.domain;

/**
 * AUDIT-QUERY-FORENSIC の出力 — AQF-OUTPUT 相当.
 *
 * <p>COBOL 88 レベル値を保持 (00=OK, 08=INVALID-INPUT, 12=IO-FAIL, 16=FATAL).</p>
 */
public record ForensicQueryOutput(
    String status,
    int rowCount,
    String queryId,
    long durationMs
) {
    public static final String OK = "00";
    public static final String INVALID_INPUT = "08";
    public static final String IO_FAIL = "12";
    public static final String FATAL = "16";

    public boolean isOk() { return OK.equals(status); }

    public static ForensicQueryOutput ok(int rows, String queryId) {
        return new ForensicQueryOutput(OK, rows, queryId, 0);
    }

    public static ForensicQueryOutput invalidInput() {
        return new ForensicQueryOutput(INVALID_INPUT, 0, "", 0);
    }

    public static ForensicQueryOutput ioFail() {
        return new ForensicQueryOutput(IO_FAIL, 0, "", 0);
    }

    public static ForensicQueryOutput fatal() {
        return new ForensicQueryOutput(FATAL, 0, "", 0);
    }
}
