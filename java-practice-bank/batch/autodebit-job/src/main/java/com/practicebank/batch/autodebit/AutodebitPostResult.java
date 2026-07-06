package com.practicebank.batch.autodebit;

import java.util.Collections;
import java.util.List;

/**
 * 1 自動引き落とし指令の POST 結果 — AD-RUN-OUTPUT 内訳相当.
 *
 * <p>POST 成功時は posted=true, errors が空.</p>
 * <p>POST 失敗時は posted=false で reason に失敗コード
 * (CL/NF/SU/HE 等 design 書の返却コード相当) を保持.</p>
 */
public record AutodebitPostResult(
    String instructionId,
    String batchId,
    boolean posted,
    String reason,
    Long amountJpy,
    List<String> errors
) {
    public AutodebitPostResult {
        errors = (errors == null) ? List.of() : List.copyOf(errors);
    }

    /** POST 成功. */
    public static AutodebitPostResult ok(String instructionId, String batchId, Long amountJpy) {
        return new AutodebitPostResult(instructionId, batchId, true, "OK", amountJpy, List.of());
    }

    /** POST 失敗. reason = NF (残高不足) / CL (口座異常) / SU (休止). */
    public static AutodebitPostResult failed(String instructionId, String batchId,
                                              Long amountJpy, String reason, List<String> errors) {
        return new AutodebitPostResult(instructionId, batchId, false, reason, amountJpy, errors);
    }
}
