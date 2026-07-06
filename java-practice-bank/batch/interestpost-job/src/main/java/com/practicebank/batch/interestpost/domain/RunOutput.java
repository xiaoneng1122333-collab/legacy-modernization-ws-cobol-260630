package com.practicebank.batch.interestpost.domain;

import java.math.BigDecimal;

/**
 * IPST-RUN-OUTPUT 相当 — IPST-RUN-MONTHEND の返却コード・集計フィールド.
 *
 * <p>COBOL 88 レベル値を保持 (00=OK, 04=PARTIAL, 08=INVALID-INPUT, 12=IO-FAIL, 16=FATAL).</p>
 */
public record RunOutput(
    String status,
    int accountsAggregated,
    int accountsPosted,
    int skippedClosed,
    int skippedProduct,
    int skippedAlready,
    int skippedHelper,
    long acRowsConsumed,
    BigDecimal totalPostedJpy,
    long durationSec
) {
    public static final String OK = "00";
    public static final String PARTIAL = "04";
    public static final String INVALID_INPUT = "08";
    public static final String IO_FAIL = "12";
    public static final String FATAL = "16";

    public RunOutput {
        if (totalPostedJpy == null) {
            totalPostedJpy = BigDecimal.ZERO;
        }
    }

    public boolean isOk() { return OK.equals(status); }
}
