package com.practicebank.batch.fee.domain;

import java.math.BigDecimal;

/**
 * 1 手数料算出対象取引の snapshot — FEE-CHARGE の FEECUR カーソル行相当.
 *
 * <p>transactions テーブルから category IN (30, 40) の PT 行をフェッチして保持する.</p>
 */
public record FeeTransactionSnapshot(
    String txnId,
    String accountNumber,
    BigDecimal amountJpy,
    String category,
    String sourceBatchId,
    int sourceSeq
) {
    /**
     * カテゴリ 30 は非課金 (design test #2).
     */
    public boolean isCategoryNonChargeable() {
        return "30".equals(category);
    }
}
