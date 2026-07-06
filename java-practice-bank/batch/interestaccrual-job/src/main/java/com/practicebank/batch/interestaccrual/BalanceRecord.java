package com.practicebank.batch.interestaccrual;

import java.time.LocalDate;

/**
 * balances テーブルの 1 行に対応する読み取り専用 DTO.
 * カラム名・型は DB スキーマ (V1__initial_schema.sql) と一致させる.
 */
public record BalanceRecord(
    String accountNumber,
    Long balanceJpy,
    Long availableJpy,
    Long holdJpy,
    String lastTxnId,
    LocalDate lastBusinessDate,
    java.sql.Timestamp updatedTs
) {
}
