package com.practicebank.batch.integrationout.domain;

/**
 * INTO-DRAIN-QUEUE の入力 — INTD-INPUT 相当.
 *
 * <p>autodebit 失敗キューメタデータ + 処理上限.</p>
 */
public record DrainQueueInput(
    String sourceFilename,
    int maxRecords,
    String mode
) {
    public static final String MODE_REAL = "R";
    public static final String MODE_MOCK = "M";

    public boolean isMock() {
        return MODE_MOCK.equals(mode);
    }

    /** デフォルト maxRecords = 10000. */
    public int effectiveMaxRecords() {
        return maxRecords <= 0 ? 10000 : maxRecords;
    }

    /** 入力妥当性検証: ファイル名必須. */
    public boolean isValid() {
        return sourceFilename != null && !sourceFilename.isBlank();
    }
}
