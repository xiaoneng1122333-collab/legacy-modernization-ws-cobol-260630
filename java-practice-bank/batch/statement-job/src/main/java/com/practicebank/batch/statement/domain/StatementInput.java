package com.practicebank.batch.statement.domain;

import java.time.LocalDate;

/**
 * STMT-INPUT 相当 — ジョブ起動パラメータ.
 */
public record StatementInput(
    String batchId,
    LocalDate businessDate,
    String mode,             // D=daily / M=monthly
    String outputFilename,
    String summaryFilename,
    boolean skipInactive
) {
    /**
     * 入力妥当性 (COBOL status=08 相当).
     * batchId, businessDate, mode, outputFilename は必須.
     * mode は "D" または "M" のみ.
     */
    public boolean isValid() {
        return batchId != null && !batchId.isBlank()
            && businessDate != null
            && mode != null && (mode.equals("D") || mode.equals("M"))
            && outputFilename != null && !outputFilename.isBlank();
    }

    public boolean isDaily() { return "D".equals(mode); }
    public boolean isMonthly() { return "M".equals(mode); }
}
