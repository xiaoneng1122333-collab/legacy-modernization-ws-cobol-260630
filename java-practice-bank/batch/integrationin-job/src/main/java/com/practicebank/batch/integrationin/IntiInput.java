package com.practicebank.batch.integrationin;

/**
 * INTI-INPUT 写像 (copybook inti-api.cpy)。
 *
 * <p>バッチドライバが渡す 8 項目を POJO にしたもの。バリデーション (INVALID-INPUT 等)
 * は {@link IntiInputValidator} が担当する。
 *
 * @param batchId           INTI-BATCH-ID           (PIC X(14), 必須)
 * @param businessDate      INTI-BUSINESS-DATE      (PIC 9(8) YYYYMMDD, 0 は NG)
 * @param inputFilename     INTI-INPUT-FILENAME     (PIC X(120), 必須)
 * @param outputFilename    INTI-OUTPUT-FILENAME    (PIC X(120), 必須)
 * @param rejectFilename    INTI-REJECT-FILENAME    (PIC X(120), 任意)
 * @param sentinelFilename  INTI-SENTINEL-FILENAME  (PIC X(120), 任意)
 * @param rejectThresholdPct INTI-REJECT-THRESHOLD-PCT (PIC 9(3) %, 既定 20)
 * @param requireSentinel   INTI-REQUIRE-SENTINEL   (Y/N, 既定 N)
 */
public record IntiInput(
        String batchId,
        long businessDate,
        String inputFilename,
        String outputFilename,
        String rejectFilename,
        String sentinelFilename,
        int rejectThresholdPct,
        boolean requireSentinel
) {
    public IntiInput {
        if (batchId == null) batchId = "";
        if (inputFilename == null) inputFilename = "";
        if (outputFilename == null) outputFilename = "";
        if (rejectFilename == null) rejectFilename = "";
        if (sentinelFilename == null) sentinelFilename = "";
        if (rejectThresholdPct < 0 || rejectThresholdPct > 100) rejectThresholdPct = 20;
    }
}
