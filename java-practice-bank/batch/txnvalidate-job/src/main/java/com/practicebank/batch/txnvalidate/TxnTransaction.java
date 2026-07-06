package com.practicebank.batch.txnvalidate;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * transactions テーブルの 1 行に対応する読み取り専用 DTO.
 * カラム名・型は DB スキーマ (V1__initial_schema.sql) と一致させる.
 */
public record TxnTransaction(
    String txnId,
    LocalDate businessDate,
    String category,
    String accountNumber,
    String counterAccountNumber,
    BigDecimal amountJpy,
    String currency,
    String description,
    String sourceSystem,
    String sourceBatchId,
    Integer sourceSeq,
    String status,
    String reversalOf,
    String createdBy,
    java.sql.Timestamp createdTs
) {
    /** self-transfer かどうか (送金人 == あて先). */
    public boolean isSelfTransfer() {
        return counterAccountNumber != null
            && !counterAccountNumber.isBlank()
            && counterAccountNumber.equals(accountNumber);
    }
}
