package com.practicebank.batch.operations.batchrun;

/**
 * OPS-BATCH-RUN-COMPLETE が受理するステータスのホワイトリスト.
 * ホワイトリスト外の値は拒否 (rc=1) する.
 */
public enum RunCompleteStatus {
    OK,
    FL,
    AB;

    /** ホワイトリスト検証. 許容しない値は null を返す. */
    public static RunCompleteStatus fromString(String raw) {
        if (raw == null) return null;
        try {
            return RunCompleteStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
