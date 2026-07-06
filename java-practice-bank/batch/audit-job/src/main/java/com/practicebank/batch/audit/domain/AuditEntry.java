package com.practicebank.batch.audit.domain;

/**
 * audit_log テーブルの 1 行 — カーソル取得の受け皿.
 */
public record AuditEntry(
    String auditId,
    String businessDate,
    String subsystem,
    String action,
    String severity,
    String accountNumber,
    String payload,
    String sourceSystem,
    String operatorUser,
    java.sql.Timestamp createdTs
) {
}
