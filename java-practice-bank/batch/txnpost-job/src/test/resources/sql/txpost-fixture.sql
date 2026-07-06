-- Test fixture for txnpost-job: transactions / balances にテストデータ
TRUNCATE postings CASCADE;
TRUNCATE transactions CASCADE;
TRUNCATE balances CASCADE;

-- システム口座 (現金・整治口) — VERIFY-SYSTEM-ACCOUNTS 相当
INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, updated_ts)
VALUES ('0000000000001', 1000000000, 1000000000, 0, NOW()); -- CASH

INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, updated_ts)
VALUES ('0000000000002', 1000000000, 1000000000, 0, NOW()); -- CLEARING

-- 顧客口座 (正常 - 入金・出金・振替・電払向け)
INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, updated_ts)
VALUES ('0001234567890', 1000000, 1000000, 0, NOW());

INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, updated_ts)
VALUES ('0001234567891', 2500000, 2500000, 0, NOW());

INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, updated_ts)
VALUES ('0001234567892', 5000000, 5000000, 0, NOW());

-- PT 取引 (status='PT') — TXPOST-RUN-BATCH 処理対象
-- 入金 1000 JPY → account 0001234567890 (cat 10)
INSERT INTO transactions (txn_id, business_date, system_ts, category, account_number,
    counter_account_number, amount_jpy, currency, description, source_system,
    source_batch_id, source_seq, status, reversal_of, created_by, created_ts)
VALUES ('TXN2026070600001', '2026-07-06', NOW(), '10', '0001234567890',
    NULL, 1000, 'JPY', 'DEPOSIT', 'BATCH', 'BATCH202607061', 1, 'PT', NULL, 'SYSTEM', NOW());

-- 出金 5000 JPY → account 0001234567891 (cat 20, 残高十分)
INSERT INTO transactions (txn_id, business_date, system_ts, category, account_number,
    counter_account_number, amount_jpy, currency, description, source_system,
    source_batch_id, source_seq, status, reversal_of, created_by, created_ts)
VALUES ('TXN2026070600002', '2026-07-06', NOW(), '20', '0001234567891',
    NULL, 5000, 'JPY', 'WITHDRAWAL', 'BATCH', 'BATCH202607061', 2, 'PT', NULL, 'SYSTEM', NOW());

-- 振替 20000 JPY → 0001234567891 → 0001234567892 (cat 30)
INSERT INTO transactions (txn_id, business_date, system_ts, category, account_number,
    counter_account_number, amount_jpy, currency, description, source_system,
    source_batch_id, source_seq, status, reversal_of, created_by, created_ts)
VALUES ('TXN2026070600003', '2026-07-06', NOW(), '30', '0001234567891',
    '0001234567892', 20000, 'JPY', 'TRANSFER', 'BATCH', 'BATCH202607061', 3, 'PT', NULL, 'SYSTEM', NOW());
