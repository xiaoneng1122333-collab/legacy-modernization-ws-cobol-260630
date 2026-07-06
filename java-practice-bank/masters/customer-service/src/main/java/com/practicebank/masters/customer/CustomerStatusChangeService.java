package com.practicebank.masters.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * CUST-STATUS-CHANGE: 顧客ステータス更新 + 監査証拠記録。
 * COBOL の「CALL AUD-WRITE (CUST_STATUS_CHANGED)」を DB audit_log INSERT に単純化する。
 */
@Service
public class CustomerStatusChangeService {

    private static final Logger log = LoggerFactory.getLogger(CustomerStatusChangeService.class);

    private static final int RC_OK = 0;
    private static final int RC_NOT_FOUND = 4;
    private static final int RC_FATAL = 16;

    private final CustomerMapper mapper;
    private final AuditLogRepository auditLog;

    public CustomerStatusChangeService(CustomerMapper mapper, AuditLogRepository auditLog) {
        this.mapper = mapper;
        this.auditLog = auditLog;
    }

    /** 返却コード付き結果。 */
    public record StatusChangeResult(int statusCode, Customer customer) {
    }

    /**
     * 顧客ステータスを更新し監査証拠を記録する。
     *
     * @param custId   変更対象の顧客 ID
     * @param newStatus 新しいステータス
     * @param businessDate 業務日付 (YYYYMMDD) — 監査証拠に記録
     * @return 00=正常 / 04=NOT-FOUND / 16=FATAL
     */
    @Transactional
    public StatusChangeResult changeStatus(String custId, String newStatus, String businessDate) {
        Optional<Customer> found = mapper.findById(custId);
        if (found.isEmpty()) {
            log.warn("CUST-STATUS-CHANGE NOT-FOUND custId={}", custId);
            return new StatusChangeResult(RC_NOT_FOUND, null);
        }

        int updated = mapper.updateStatus(custId, newStatus);
        if (updated != 1) {
            log.error("CUST-STATUS-CHANGE FATAL update failed custId={}", custId);
            throw new CustomerStatusChangeException("update failed for custId=" + custId, RC_FATAL);
        }

        Customer after = mapper.findById(custId).orElseThrow();
        auditLog.append(new AuditEntry(custId, "CUST_STATUS_CHANGED",
                after.custStatus(), businessDate));
        log.info("CUST-STATUS-CHANGE complete custId={} newStatus={}", custId, newStatus);

        return new StatusChangeResult(RC_OK, after);
    }

    public static class CustomerStatusChangeException extends RuntimeException {
        private final int statusCode;

        public CustomerStatusChangeException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
