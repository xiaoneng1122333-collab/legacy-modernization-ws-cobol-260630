-- Test fixture for fee-job: FEE-CHARGE 正常系 (design test #1, #2, #3, #5).
-- 前提: V1__initial_schema.sql のテーブルが存在すること.

TRUNCATE transactions, postings, balances, fee_schedules CASCADE;

-- fee_schedules: カテゴリ 30 (tier1=110) / 40 (tier2=440, tier3=880).
-- tier1 は手数料 0 扱い (design test #3).
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('30', '1', '2026-01-01', 110);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('30', '2', '2026-01-01', 220);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('40', '1', '2026-01-01', 0);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('40', '2', '2026-01-01', 440);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('40', '3', '2026-01-01', 880);

-- 口座マスタ (balances): 001 tier2 判定, 002 tier3 判定, 003 非課金(category30), 004 残高不足.
INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES
    ('0001234567001', 1000000, 1000000, 0, NULL, NULL, NOW()),
    ('0001234567002', 2000000, 2000000, 0, NULL, NULL, NOW()),
    ('0001234567003', 3000000, 3000000, 0, NULL, NULL, NOW()),
    ('0001234567004',      50,      50, 0, NULL, NULL, NOW()),
    ('0001234567005', 5000000, 5000000, 0, NULL, NULL, NOW());

-- 取引明細 (transactions, category IN (30, 40), status='PT').
-- tier判定は txn_id 末尾 1 文字 (1=tier1, 2=tier2, 3=tier3).
-- acct 0001234567001: category=40 tier2 → charge ¥440 (1件のみ id=...0011 → tier1=0... この検証のため tier=2)
-- acct 0001234567002: category=40 tier3 → charge ¥880
-- acct 0001234567003: category=30 tier1 → non-chargeable (category30)
-- acct 0001234567004: category=40 tier2 → charge ¥440 しかい balance=50 < 440 → NSF skip
-- acct 0001234567005: category=40 tier3 → charge ¥880
INSERT INTO transactions
    (txn_id, business_date, system_ts, category, account_number,
     counter_account_number, amount_jpy, currency, description,
     source_system, source_batch_id, source_seq, status, reversal_of, created_by, created_ts)
VALUES
    ('FEE-TX-20260601011', '2026-06-13', NOW(), '40', '0001234567001',
     '9999999999999', 5000000, 'JPY', 'overseas-txn-001', 'BATCH', 'FEE-TEST-001', 1, 'PT', NULL, 'test-fee', NOW()),
    ('FEE-TX-20260601022', '2026-06-13', NOW(), '40', '0001234567002',
     '9999999999999', 5000000, 'JPY', 'overseas-txn-002', 'BATCH', 'FEE-TEST-001', 2, 'PT', NULL, 'test-fee', NOW()),
    ('FEE-TX-20260601031', '2026-06-13', NOW(), '30', '0001234567003',
     '9999999999999', 5000000, 'JPY', 'domestic-txn-003', 'BATCH', 'FEE-TEST-001', 3, 'PT', NULL, 'test-fee', NOW()),
    ('FEE-TX-20260601042', '2026-06-13', NOW(), '40', '0001234567004',
     '9999999999999', 5000000, 'JPY', 'overseas-txn-004', 'BATCH', 'FEE-TEST-001', 4, 'PT', NULL, 'test-fee', NOW()),
    ('FEE-TX-20260601053', '2026-06-13', NOW(), '40', '0001234567005',
     '9999999999999', 5000000, 'JPY', 'overseas-txn-005', 'BATCH', 'FEE-TEST-001', 5, 'PT', NULL, 'test-fee', NOW());
