package com.practicebank.batch.txnpost;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * postings テーブルの 1 行に対応する読み取り専用 DTO.
 *
 * <p>dual-entry で 2 行 (DR + CR) を生成する TXPOST-RUN-BATCH の出力表現.</p>
 */
public record PostingRecord(
    String postingId,
    String txnId,
    Short lineNo,
    String accountNumber,
    BigInteger debitJpy,
    BigInteger creditJpy,
    String postingRole,
    LocalDate businessDate,
    LocalDateTime createdTs
) {
}
