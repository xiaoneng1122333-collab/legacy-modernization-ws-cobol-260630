package com.practicebank.masters.customer;

/** 監査証拠（AUD-WRITE 相当）。 */
public record AuditEntry(
        String custId,
        String eventType,
        String newStatus,
        String businessDate
) {
}
