-- Test fixture: audit_log に検索・集計対象データ
TRUNCATE audit_log CASCADE;

-- 正常系: BY-DAY / BY-SUBSYSTEM 検索対象
INSERT INTO audit_log (audit_id, business_date, subsystem, action, severity, account_number, payload, source_system, operator_user, created_ts)
VALUES
('A001', '20260615', '17-statement', 'STMT_GEN_END', 'I', '0001234567890', '{"count":5}', '17-statement', 'SYSTEM', NOW()),
('A002', '20260615', '17-statement', 'STMT_GEN_END', 'I', '0001234567891', '{"count":3}', '17-statement', 'SYSTEM', NOW()),
('A003', '20260615', '20-integrationout', 'PUBLISH', 'W', '0001234567890', '{"event":"txn.posted"}', '20-integrationout', 'SYSTEM', NOW()),
('A004', '20260616', '01-txnpost', 'TXN_POST', 'I', '0001234567890', '{"txnId":"TXN001"}', '01-txnpost', 'SYSTEM', NOW()),
('A005', '20260616', '01-txnpost', 'TXN_POST', 'E', '0001234567892', '{"error":"NF"}', '01-txnpost', 'SYSTEM', NOW()),
('A006', '20260617', '03-autodebit', 'DEBIT_RUN', 'C', '0001234567890', '{"batchId":"B001"}', '03-autodebit', 'SYSTEM', NOW());
