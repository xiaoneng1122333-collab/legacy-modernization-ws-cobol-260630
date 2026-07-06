package com.practicebank.batch.interestpost.domain;

import java.time.LocalDate;

/**
 * IPST-RUN-INPUT 相当 — ジョブ起動パラメータ.
 */
public record RunInput(
    String batchId,
    LocalDate businessDate,
    String summaryFilename,
    String checkpointFilename
) {
    /**
     * 入力妥当性 (COBOL status=08 相当).
     * batchId と businessDate は必須.
     */
    public boolean isValid() {
        return batchId != null && !batchId.isBlank()
            && businessDate != null
            && !businessDate.isEqual(LocalDate.of(1970, 1, 1));
    }
}
