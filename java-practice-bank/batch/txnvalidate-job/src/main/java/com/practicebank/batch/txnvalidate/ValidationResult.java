package com.practicebank.batch.txnvalidate;

import java.util.Collections;
import java.util.List;

/**
 * 1 トランザクション明細のバリデーション結果 — TXVAL-BATCH-OUTPUT 相当.
 *
 * <p>transactionId は VARCHAR/CHAR(18) と合わる String 型.</p>
 * <p>不正がない場合は errors が空リスト, {@code valid=true}.</p>
 */
public record ValidationResult(
    String transactionId,
    String batchId,
    boolean valid,
    List<String> errors
) {
    public ValidationResult {
        errors = (errors == null) ? List.of() : List.copyOf(errors);
    }

    /** 妥当 (エラーなし) */
    public static ValidationResult ok(String txnId, String batchId) {
        return new ValidationResult(txnId, batchId, true, List.of());
    }

    /** 拒否 (エラーあり) */
    public static ValidationResult rejected(String txnId, String batchId, List<String> errors) {
        return new ValidationResult(txnId, batchId, false, errors);
    }
}
