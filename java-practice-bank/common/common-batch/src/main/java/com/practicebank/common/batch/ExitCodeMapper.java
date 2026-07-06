package com.practicebank.common.batch;

import org.springframework.batch.core.ExitStatus;

/**
 * COBOL の RETURN-CODE を Spring Batch の ExitStatus に変換する。
 * COBOL との等価性検証のため、同じ終了コード体系を維持する。
 *
 * <pre>
 *   00 → COMPLETED   (正常)
 *   04 → COMPLETED   (処理対象なし / NOT-FOUND)
 *   08 → FAILED      (入力エラー / INVALID-INPUT)
 *   12 → FAILED      (I/O エラー / IO-FAIL)
 *   16 → FAILED      (致命的エラー / FATAL)
 * </pre>
 */
public final class ExitCodeMapper {

    public static ExitStatus fromReturnCode(int returnCode) {
        return switch (returnCode) {
            case 0 -> ExitStatus.COMPLETED;
            case 4 -> new ExitStatus("COMPLETED", "No records processed (NOT-FOUND)");
            case 8 -> new ExitStatus("FAILED", "Invalid input (INVALID-INPUT)");
            case 12 -> new ExitStatus("FAILED", "I/O failure (IO-FAIL)");
            case 16 -> new ExitStatus("FAILED", "Fatal error (FATAL)");
            default -> new ExitStatus("FAILED", "Unknown return code: " + returnCode);
        };
    }

    private ExitCodeMapper() {}
}
