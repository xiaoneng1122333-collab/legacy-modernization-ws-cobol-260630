package com.practicebank.batch.fee.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 1 手数料仕訳 (transactions + postings 2 行) を表す — FEE-CHARGE の POST-PARA 相当.
 *
 * <p>借方 (DR): 顧客口座から fee_jpy を減算.
 * 貸方 (CR): 手数料収益口座 (fee_rev_account) に fee_jpy を加算.</p>
 */
public record FeePosting(
    String txnId,
    String accountNumber,
    String counterAccountNumber,
    BigDecimal feeJpy,
    LocalDate businessDate,
    String sourceBatchId,
    int sourceSeq,
    String description
) {
    public boolean hasPositiveFee() {
        return feeJpy != null && feeJpy.compareTo(BigDecimal.ZERO) > 0;
    }
}
