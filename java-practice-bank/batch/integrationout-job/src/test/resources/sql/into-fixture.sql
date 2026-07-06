-- Test fixture: autodebit_failed_queue + audit_log にバリデーション対象データ
TRUNCATE autodebit_failed_queue, audit_log CASCADE;

-- 正常レコード (drain 成功)
INSERT INTO autodebit_failed_queue
(queue_id, txn_id, account_number, amount_jpy, failure_reason, created_ts, status)
VALUES
('Q001', 'TXN202607060001', '0001234567890', 5000, 'NF', NOW(), 'PD'),
('Q002', 'TXN202607060002', '0001234567890', 12000, 'HE', NOW(), 'PD'),
('Q003', 'TXN202607060003', '0001234567890', 300, 'CL', NOW(), 'PD');

-- Phase 2: 失敗コード "FATAL" を含むレコード (drain 失敗扱い)
INSERT INTO autodebit_failed_queue
(queue_id, txn_id, account_number, amount_jpy, failure_reason, created_ts, status)
VALUES
('Q004', 'TXN202607060004', '0001234567890', 999999, 'FATAL', NOW(), 'PD');
