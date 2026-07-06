package com.practicebank.batch.txnsortmerge;

import java.math.BigDecimal;

/**
 * 1 ペアの 2-way マージ結果.
 *
 * @param source        "SORTED" または "RECON"
 * @param txnId         出力先 txn_id (マージ後の主キー)
 * @param accountNumber payer-acct
 * @param sourceSeq     source_seq
 * @param amountJpy     amount_jpy (金額保存量)
 * @param duplicate     重複フラグ — true の場合は error 退避
 */
public record MergeResult(
    String source,
    String txnId,
    String accountNumber,
    Integer sourceSeq,
    BigDecimal amountJpy,
    boolean duplicate
) {
}
