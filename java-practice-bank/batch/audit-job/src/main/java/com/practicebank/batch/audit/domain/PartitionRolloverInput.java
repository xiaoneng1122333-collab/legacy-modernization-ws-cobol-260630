package com.practicebank.batch.audit.domain;

/**
 * AUDIT-PARTITION-ROLLOVER の入力 — APR-INPUT 相当.
 */
public record PartitionRolloverInput(
    String operatorUser,
    int retentionDays,
    boolean dryRun,
    boolean enableDetach
) {
    public static final String DRY_RUN_YES = "Y";
    public static final String DRY_RUN_NO = "N";
    public static final String DETACH_YES = "Y";
    public static final String DETACH_NO = "N";

    public String effectiveOperator() {
        return (operatorUser == null || operatorUser.isBlank()) ? "SYSTEM" : operatorUser.trim();
    }

    public int effectiveRetentionDays() {
        return retentionDays <= 0 ? 30 : retentionDays;
    }
}
