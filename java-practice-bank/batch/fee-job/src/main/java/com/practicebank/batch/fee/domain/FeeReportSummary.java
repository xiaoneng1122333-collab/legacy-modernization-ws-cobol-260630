package com.practicebank.batch.fee.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * FEE-REPORT-OUTPUT / file 出力内容相当 — FEE-REPORT-SUMMARY の照合結果.
 *
 * <p>conservation=保存量検验: transactions PT 合計 == balances 手数料収益口座 現在高.
 * ExecutionContext に格納するため Serializable を implements する.</p>
 */
public record FeeReportSummary(
    String status,
    int totalCharges,
    BigDecimal totalFeeJpy,
    BigDecimal feeRevBalance,
    String conservationPass,
    long durationSec
) implements Serializable {
    public static final String OK = "00";
    public static final String CONSERVATION_WARN = "04";
    public static final String INVALID_INPUT = "08";
    public static final String IO_FAIL = "12";

    public boolean isOk() { return OK.equals(status); }
}
