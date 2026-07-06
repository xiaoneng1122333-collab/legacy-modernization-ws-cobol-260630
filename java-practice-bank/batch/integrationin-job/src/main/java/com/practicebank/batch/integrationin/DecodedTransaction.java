package com.practicebank.batch.integrationin;

import com.practicebank.common.domain.Money;

import java.time.LocalDate;

/**
 * デコード済みトランザクション 1 件 (COBOL の 600B デコード後レコードに対応)。
 *
 * <p>transactions テーブルに書き出すための DTO。amount は {@link Money} を経由して
 * JPY の整合 (小数点なし) をコンパイル時に保つ設計意向だが、Phase 2 では数値チェック
 * (E108 金額ゼロ) の中身自体は int で持つ。
 */
public record DecodedTransaction(
        String recType,
        long seq,
        String category,
        long amountJpy,
        String currency,
        String payerAccount,
        String payeeAccount,
        String branchCode,
        String productCode,
        String description,
        String sourceBank,
        String sourceBranch,
        LocalDate businessDate,
        String batchId
) {
}
