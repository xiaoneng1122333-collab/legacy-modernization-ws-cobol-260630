package com.practicebank.batch.audit.domain;

/**
 * AUDIT-SUMMARY-REPORT の入力 — ASR-INPUT 相当.
 */
public record SummaryReportInput(
    String dateStart,
    String dateEnd,
    String mode,
    String outputFilename
) {
    public static final String MODE_BY_DAY = "D";
    public static final String MODE_BY_SUBSYSTEM = "S";

    /** 集計モード: 未指定時は BY-DAY. */
    public String effectiveMode() {
        if (mode == null || mode.isBlank()) return MODE_BY_DAY;
        return switch (mode.trim()) {
            case "D", "S" -> mode.trim();
            default -> MODE_BY_DAY;
        };
    }

    /**
     * 入力妥当性検証: 日付 0 禁止.
     * ファイル名チェックは Phase 2 ログ出力モードでは省略 (ファイル I/O なし).
     */
    public boolean isValid() {
        if (dateStart == null || dateStart.isBlank() || "0".equals(dateStart.trim())) return false;
        if (dateEnd == null || dateEnd.isBlank()) return false;
        return true;
    }
}
