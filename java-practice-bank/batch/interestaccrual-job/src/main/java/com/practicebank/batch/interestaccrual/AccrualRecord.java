package com.practicebank.batch.interestaccrual;

import java.math.BigDecimal;

/**
 * interest_accruals テーブルの 1 行に対応する読み取り専用 DTO — IACR-RUN-OUTPUT に相当.
 *
 * <p>フィールドは iacr-api.cpy の IACR-RUN-OUTPUT 集計キーと
 * DB スキーマ interest_accruals テーブルのカラム名に対応.
 */
public record AccrualRecord(
    String businessDate,
    String accountNumber,
    String productCode,
    Long principalJpy,
    BigDecimal rate,
    Short days,
    Long accruedJpy,
    String status
) {
}
