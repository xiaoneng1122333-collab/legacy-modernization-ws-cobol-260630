-- Test fixture for interestaccrual-job: balances にテストデータ
-- (正常・残高ゼロ・システムブラックリスト各種混在)
TRUNCATE interest_accruals CASCADE;
TRUNCATE balances CASCADE;

-- 正常: 預金 100000 JPY (利息対象)
INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES ('0001234567890', 100000, 100000, 0, 'TXN20260706000001', '2026-07-06', NOW());

-- 正常: 預金 250000 JPY (利息対象)
INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES ('0001234567891', 250000, 250000, 0, 'TXN20260706000002', '2026-07-06', NOW());

-- 正常: 預金 5000000 JPY (利息対象)
INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES ('0001234567892', 5000000, 5000000, 0, 'TXN20260706000003', '2026-07-06', NOW());

-- 残高ゼロ (INELIGIBLE-BALANCE 相当 - IACR ではスキップ)
INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES ('0001234567893', 0, 0, 0, NULL, NULL, NOW());

-- システムブラックリスト (SYS_SKIP 相当)
INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES ('0000000000000', 99999999, 99999999, 0, NULL, NULL, NOW());

INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES ('0000000000001', 88888888, 88888888, 0, NULL, NULL, NOW());

INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES ('0000000000002', 77777777, 77777777, 0, NULL, NULL, NOW());

INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES ('0000000000003', 66666666, 66666666, 0, NULL, NULL, NOW());
