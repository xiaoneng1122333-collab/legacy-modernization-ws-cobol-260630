package com.practicebank.batch.interestpost.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 1 口座あたり 1 件の INTEREST 仕訳 (transactions + postings) を表す — IPST-RUN-MONTHEND の POST-PARA 相当.
 *
 * <p>冪等キー: (source_batch_id, account_number) で重複確認する.</p>
 */
public record InterestPosting(
    String txnId,
    String accountNumber,
    String sysExpenseAccount,
    BigDecimal amountJpy,
    LocalDate businessDate,
    String sourceBatchId,
    int acRowsConsumed,
    int sourceSeq
) {
    /** 仕訳金額が正 (利息 > 0) かどうか. */
    public boolean hasPositiveAmount() {
        return amountJpy != null && amountJpy.compareTo(BigDecimal.ZERO) > 0;
    }
}
