package com.practicebank.batch.statement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;


/**
 * 帳票明細行 1 行分 — STMT-GENERATE-BATCH の DETAIL-LINE 相当.
 *
 * <p>customer / branch 名はマスタキャッシュ解決済み.</p>
 */
public record StatementLine(
    String accountNumber,
    String customerName,
    String branchName,
    String txnId,
    LocalDate businessDate,
    String description,
    BigDecimal amountJpy,
    BigDecimal runningBalance
) {
}
