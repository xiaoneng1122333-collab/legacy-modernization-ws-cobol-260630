package com.practicebank.batch.txnpost;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * transactions テーブルの 1 行に対応する読み取り専用 DTO.
 * カラム名・型は DB スキーマ (V1__initial_schema.sql) の transactions テーブルと一致.
 *
 * <p>設計書 tx-post-api.cpy の TXPOST-RUN-INPUT / OUTPUT に相当する Java 表現.</p>
 */
public record TransactionRecord(
    String txnId,
    LocalDate businessDate,
    LocalDateTime systemTs,
    String category,
    String accountNumber,
    String counterAccountNumber,
    BigInteger amountJpy,
    String currency,
    String description,
    String sourceSystem,
    String sourceBatchId,
    Integer sourceSeq,
    String status,
    String reversalOf,
    String createdBy,
    LocalDateTime createdTs
) {
}
