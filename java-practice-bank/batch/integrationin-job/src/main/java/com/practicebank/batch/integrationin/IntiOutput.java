package com.practicebank.batch.integrationin;

/**
 * INTI-OUTPUT 写像 (copybook inti-api.cpy)。
 *
 * <p>バッチ終了時に呼び出し元へ返す 7 項目。ステータス体系は COBOL と同一:
 * <pre>
 *   00 → OK
 *   01 → NO-INPUT-READY (センチネル不在)
 *   04 → PARTIAL (トレイラ不一致 / 拒否率超過)
 *   08 → INVALID-INPUT
 *   12 → IO-FAIL
 *   16 → FATAL
 * </pre>
 */
public record IntiOutput(
        String status,
        long recordsRead,
        long detailsDecoded,
        long detailsRejected,
        int rejectPct,
        boolean checksumMatch,
        int durationSec
) {
    /** 初期値 (INTI-OK 相当) */
    public static IntiOutput init() {
        return new IntiOutput("00", 0, 0, 0, 0, true, 0);
    }

    public boolean isOk() {
        return "00".equals(status);
    }
}
