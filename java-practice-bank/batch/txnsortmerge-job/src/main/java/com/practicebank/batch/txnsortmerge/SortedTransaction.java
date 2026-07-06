package com.practicebank.batch.txnsortmerge;

import java.math.BigDecimal;

/**
 * ソート済みの取引明細を 1 行で表す DTO — TXSM-SORT-BATCH の中間出力相当.
 * ソートキー: (accountNumber ASC, sourceSeq ASC).
 *
 * @param txnId           transactions.txn_id
 * @param accountNumber   transactions.account_number (payer-acct)
 * @param sourceSeq       transactions.source_seq
 * @param amountJpy       transactions.amount_jpy
 * @param sortedSeq      ソート後の連番 (1-based)
 */
public record SortedTransaction(
    String txnId,
    String accountNumber,
    Integer sourceSeq,
    BigDecimal amountJpy,
    long sortedSeq
) {
}
