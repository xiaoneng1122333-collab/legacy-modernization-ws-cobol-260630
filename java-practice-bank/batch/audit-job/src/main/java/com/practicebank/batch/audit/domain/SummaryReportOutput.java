package com.practicebank.batch.audit.domain;

/**
 * AUDIT-SUMMARY-REPORT の出力 — ASR-OUTPUT 相当.
 *
 * <p>COBOL 88 レベル値を保持 (00=OK, 08=INVALID-INPUT, 12=IO-FAIL, 16=FATAL).</p>
 */
public record SummaryReportOutput(
    String status,
    int groupCount,
    long totalRows
) {
    public static final String OK = "00";
    public static final String INVALID_INPUT = "08";
    public static final String IO_FAIL = "12";
    public static final String FATAL = "16";

    public boolean isOk() { return OK.equals(status); }

    public static SummaryReportOutput ok(int groups, long total) {
        return new SummaryReportOutput(OK, groups, total);
    }

    public static SummaryReportOutput invalidInput() {
        return new SummaryReportOutput(INVALID_INPUT, 0, 0);
    }

    public static SummaryReportOutput ioFail() {
        return new SummaryReportOutput(IO_FAIL, 0, 0);
    }

    public static SummaryReportOutput fatal() {
        return new SummaryReportOutput(FATAL, 0, 0);
    }
}
