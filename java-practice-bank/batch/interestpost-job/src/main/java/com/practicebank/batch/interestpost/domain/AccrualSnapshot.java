package com.practicebank.batch.interestpost.domain;

import java.math.BigDecimal;

/**
 * interest_accruals テーブルの AC 行をアカウント単位で集計した結果 — IPST-RUN-MONTHEND のスナップショット行相当.
 *
 * <p>カラム名・型は DB スキーマ (V1__initial_schema.sql) と一致させる.</p>
 */
public record AccrualSnapshot(
    String accountNumber,
    String productCode,
    BigDecimal accruedJpy,
    int acRowCount
) {
    /** 仕訳対象商品かどうか (product_code = "001"). */
    public boolean isPostableProduct() {
        return "001".equals(productCode);
    }
}
