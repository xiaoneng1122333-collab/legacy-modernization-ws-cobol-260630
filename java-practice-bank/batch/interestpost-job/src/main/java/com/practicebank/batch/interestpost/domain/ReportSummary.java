package com.practicebank.batch.interestpost.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * IPST-REPORT-OUTPUT 相当 — IPST-REPORT-SUMMARY の照合結果.
 *
 * <p>conservation=保存量検証: transactions PT 合計 == interest_accruals PT 合計.
 * ExecutionContext に格納するため Serializable を implements する.</p>
 */
public record ReportSummary(
    String status,
    int totalPosted,
    BigDecimal totalPostedJpy,
    int ptRowCount,
    int acRemaining,
    BigDecimal accruedSum,
    boolean conservationPass,
    long durationSec
) implements Serializable {
    public static final String OK = "00";
    public static final String CONSERVATION_WARN = "04";
    public static final String INVALID_INPUT = "08";
    public static final String IO_FAIL = "12";

    public boolean isOk() { return OK.equals(status); }
}
