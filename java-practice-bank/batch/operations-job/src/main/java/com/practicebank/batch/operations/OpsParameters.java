package com.practicebank.batch.operations;

import java.time.LocalDate;

/**
 * OPS-BATCH-DAILY の入力パラメータ (OPB-INPUT 相当).
 * COBOL は定数長 PIC だが Java は LocalDate / String / boolean.
 */
public record OpsParameters(
    String batchId,
    LocalDate businessDate,
    boolean dryRun
) {
    public String businessDateYYYYMMDD() {
        return businessDate == null
            ? ""
            : String.format("%04d%02d%02d", businessDate.getYear(), businessDate.getMonthValue(), businessDate.getDayOfMonth());
    }

    /** OPB-OUT-STEPS-RUN 相当 — 正常完了時は全ステップ数を返す. */
    public int expectedStepsRun() {
        return 10; // 19-INTI, 13-IACR, 15-AD, 16-FEE, 17-STMT, 20-DRAIN, + 4 bookkeeping ops
    }
}
