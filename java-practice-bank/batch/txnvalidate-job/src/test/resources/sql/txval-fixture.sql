-- Test fixture: transactions にバリデーション対象データ (正常/異常混在)
-- COBOL 設計 txval-validate-batch-bd.md 正常系 #1, #2, #4 と異常系 #2, #4, #6, #7, #8, #9, #13 相当
TRUNCATE transactions, txn_validation_reject CASCADE;

-- 正常: 預金 1000 JPY category=10
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000001', '2026-07-06', '2026-07-06 09:00:00', '10', '0001234567890', NULL,
 1000, 'JPY', 'deposit', 'TXVAL', 'BATCH20260706', 1,
 'PT', NULL, 'txval-job', NOW());

-- 正常: 払出 5000 JPY category=20
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000002', '2026-07-06', '2026-07-06 09:00:01', '20', '0001234567890', NULL,
 5000, 'JPY', 'withdrawal', 'TXVAL', 'BATCH20260706', 2,
 'PT', NULL, 'txval-job', NOW());

-- 振替 2000 あて先あり category=30
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000003', '2026-07-06', '2026-07-06 09:00:02', '30', '0001234567890', '0009876543210',
 2000, 'JPY', 'transfer', 'TXVAL', 'BATCH20260706', 3,
 'PT', NULL, 'txval-job', NOW());

-- 異常: カテゴリ 99 (E002)
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000101', '2026-07-06', '2026-07-06 09:01:00', '99', '0001234567890', NULL,
 1000, 'JPY', 'bad category', 'TXVAL', 'BATCH20260706', 4,
 'PT', NULL, 'txval-job', NOW());

-- 異常: 振替だがあて先なし (E007)
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000102', '2026-07-06', '2026-07-06 09:01:01', '30', '0001234567890', NULL,
 3000, 'JPY', 'missing counter', 'TXVAL', 'BATCH20260706', 5,
 'PT', NULL, 'txval-job', NOW());

-- 異常: 金額ゼロ (E009)
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000103', '2026-07-06', '2026-07-06 09:01:02', '10', '0001234567890', NULL,
 0, 'JPY', 'zero amount', 'TXVAL', 'BATCH20260706', 6,
 'PT', NULL, 'txval-job', NOW());

-- 異常: 金額超過 (E010)
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000104', '2026-07-06', '2026-07-06 09:01:03', '10', '0001234567890', NULL,
 200000000, 'JPY', 'amount > 99999999', 'TXVAL', 'BATCH20260706', 7,
 'PT', NULL, 'txval-job', NOW());

-- 異常: 土曜日付与 (E012) — 2026-07-11 is Saturday
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000105', '2026-07-11', '2026-07-11 09:01:04', '10', '0001234567890', NULL,
 1000, 'JPY', 'weekend', 'TXVAL', 'BATCH20260706', 8,
 'PT', NULL, 'txval-job', NOW());

-- 異常: 通貨 USD (E013)
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000106', '2026-07-06', '2026-07-06 09:01:05', '10', '0001234567890', NULL,
 1000, 'USD', 'bad currency', 'TXVAL', 'BATCH20260706', 9,
 'PT', NULL, 'txval-job', NOW());

-- 異常: カテゴリ 10 にあり不要なあて先あり (E018)
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000107', '2026-07-06', '2026-07-06 09:01:06', '10', '0001234567890', '0009876543210',
 1000, 'JPY', 'counter unexpected', 'TXVAL', 'BATCH20260706', 10,
 'PT', NULL, 'txval-job', NOW());

-- 異常: 自己送金 (E008)
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000108', '2026-07-06', '2026-07-06 09:01:07', '30', '0001234567890', '0001234567890',
 1000, 'JPY', 'self-transfer', 'TXVAL', 'BATCH20260706', 11,
 'PT', NULL, 'txval-job', NOW());
