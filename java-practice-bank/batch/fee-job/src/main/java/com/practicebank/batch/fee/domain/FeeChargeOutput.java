package com.practicebank.batch.fee.domain;

import java.math.BigDecimal;

/**
 * FEE-CHARGE-OUTPUT 相当 — FEE-CHARGE の返却コード・集計フィールド.
 *
 * <p>COBOL 88 レベル値を保持 (00=OK, 04=PARTIAL, 08=INVALID-INPUT, 12=IO-FAIL, 16=FATAL).</p>
 */
public record FeeChargeOutput(
    String status,
    int txnsScanned,
    int chargesPosted,
    int skippedNoFee,
    int skippedClosed,
    int skippedNsf,
    int skippedAlready,
    int skippedHelper,
    BigDecimal totalFeeJpy,
    long durationSec
) {
    public static final String OK = "00";
    public static final String PARTIAL = "04";
    public static final String INVALID_INPUT = "08";
    public static final String IO_FAIL = "12";
    public static final String FATAL = "16";

    public FeeChargeOutput {
        if (totalFeeJpy == null) {
            totalFeeJpy = BigDecimal.ZERO;
        }
    }

    public boolean isOk() { return OK.equals(status); }
}
