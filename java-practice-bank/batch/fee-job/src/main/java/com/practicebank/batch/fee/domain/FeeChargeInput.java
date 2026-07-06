package com.practicebank.batch.fee.domain;

import java.time.LocalDate;

/**
 * FEE-CHARGE-INPUT 相当 — ジョブ起動パラメータ.
 */
public record FeeChargeInput(
    String batchId,
    LocalDate businessDate,
    String summaryFilename
) {
    /**
     * 入力妥当性 (COBOL status=08 相当).
     * batchId と businessDate は必須.
     */
    public boolean isValid() {
        return batchId != null && !batchId.isBlank()
            && businessDate != null;
    }
}
