package com.practicebank.batch.fee.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 1 手数料算出対象取引の処理結果 — FEE-CHARGE の返却相当.
 *
 * <p>POST 成功時は posted=true, skipReason=null.
 * POST 失敗 (スキップ) 時は posted=false に skipReason を保持.</p>
 * <ul>
 *   <li>NF — no-fee (category30 / tier1 / fee=0)</li>
 *   <li>CL — closed / 口座不在</li>
 *   <li>NS — NSF 残高不足</li>
 *   <li>AL — already (重複仕訳)</li>
 *   <li>HE — helper 却下 (Phase 2 では未使用)</li>
 * </ul>
 */
public record FeeProcessResult(
    String txnId,
    String accountNumber,
    String counterAccountNumber,
    BigDecimal feeJpy,
    LocalDate businessDate,
    String sourceBatchId,
    int sourceSeq,
    String description,
    boolean posted,
    String skipReason,
    List<String> errors
) {
    public FeeProcessResult {
        errors = (errors == null) ? List.of() : List.copyOf(errors);
    }

    /** POST 成功. */
    public static FeeProcessResult posted(FeePosting posting) {
        return new FeeProcessResult(
            posting.txnId(), posting.accountNumber(), posting.counterAccountNumber(),
            posting.feeJpy(), posting.businessDate(), posting.sourceBatchId(),
            posting.sourceSeq(), posting.description(),
            true, null, List.of());
    }

    /** POST 失敗 — スキップ. */
    public static FeeProcessResult skipped(String txnId, String accountNumber,
                                            BigDecimal feeJpy, String reason) {
        return new FeeProcessResult(
            txnId, accountNumber, null, feeJpy, null, null, 0,
            null, false, reason, List.of(reason));
    }
}
