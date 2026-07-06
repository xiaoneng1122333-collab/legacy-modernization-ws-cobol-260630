package com.practicebank.batch.audit.domain;

/**
 * AUDIT-QUERY-FORENSIC の入力 — AQF-INPUT 相当.
 */
public record ForensicQueryInput(
    String dateStart,
    String dateEnd,
    String subsystem,
    String action,
    String severity,
    String accountFilter,
    int maxRows,
    String outputFormat,
    String outputFilename,
    String operatorUser
) {
    public static final String FMT_TEXT = "TEXT";
    public static final String FMT_CSV = "CSV ";
    public static final String FMT_JSON = "JSON";

    public static final String SEV_ANY = " ";
    public static final String SEV_INFO = "I";
    public static final String SEV_WARN = "W";
    public static final String SEV_ERROR = "E";
    public static final String SEV_CRITICAL = "C";

    public int effectiveMaxRows() {
        return maxRows <= 0 ? 1000 : maxRows;
    }

    /** 入力妥当性検証: 日付逆転・フォーマット不正等. */
    public boolean isValid() {
        if (dateStart == null || dateStart.isBlank()) return false;
        if (dateEnd == null || dateEnd.isBlank()) return false;
        // 日付逆転チェック
        if (dateStart.compareTo(dateEnd) > 0) return false;
        // フォーマットチェック
        if (outputFormat == null) return false;
        return switch (outputFormat.trim()) {
            case "TEXT", "CSV", "JSON" -> true;
            default -> false;
        };
    }
}
