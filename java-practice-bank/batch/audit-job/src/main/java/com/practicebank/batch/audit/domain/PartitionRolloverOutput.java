package com.practicebank.batch.audit.domain;

/**
 * AUDIT-PARTITION-ROLLOVER の出力 — APR-OUTPUT 相当.
 *
 * <p>COBOL 88 レベル値を保持 (00=OK, 08=INVALID-INPUT, 16=FATAL).</p>
 */
public record PartitionRolloverOutput(
    String status,
    int createdCount,
    int detachedCount,
    String nextPartition
) {
    public static final String OK = "00";
    public static final String INVALID_INPUT = "08";
    public static final String FATAL = "16";

    public boolean isOk() { return OK.equals(status); }

    public static PartitionRolloverOutput ok(int created, int detached, String nextPartition) {
        return new PartitionRolloverOutput(OK, created, detached, nextPartition);
    }

    public static PartitionRolloverOutput fatal() {
        return new PartitionRolloverOutput(FATAL, 0, 0, "");
    }

    /** 次月初日からパーティション名を算出: audit_log_YYYYMM 形式. */
    public static String computeNextPartitionName(java.time.LocalDate base) {
        java.time.LocalDate firstOfNextMonth = base.plusMonths(1).withDayOfMonth(1);
        return String.format("audit_log_%04d%02d",
            firstOfNextMonth.getYear(), firstOfNextMonth.getMonthValue());
    }

    /** 保持期限日を算出 (base date - retentionDays). */
    public static java.time.LocalDate computeHorizon(java.time.LocalDate base, int retentionDays) {
        return base.minusDays(retentionDays);
    }
}
