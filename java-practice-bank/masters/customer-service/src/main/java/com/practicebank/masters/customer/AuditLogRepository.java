package com.practicebank.masters.customer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** audit_log テーブルへ監査証拠を記録する。COBOL の AUD-WRITE を DB INSERT で代替する。 */
@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbc;

    public AuditLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(AuditEntry entry) {
        jdbc.update(
                "INSERT INTO audit_log (cust_id, event_type, new_status, business_date) VALUES (?, ?, ?, ?)",
                entry.custId(),
                entry.eventType(),
                entry.newStatus(),
                entry.businessDate()
        );
    }
}
