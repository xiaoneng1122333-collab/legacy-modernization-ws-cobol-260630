-- Test fixture for interestpost-job: IPST-RUN-MONTHEND 正常系 #1, #2, #3 相当.
-- 前提: V1__initial_schema.sql のテーブルが存在すること.

TRUNCATE transactions, interest_accruals, balances CASCADE;

-- 口座マスタ (balances): 001 商品 2 口座 + 003 商品 1 口座
INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES
    ('0001234567890', 1000000, 1000000, 0, NULL, NULL, NOW()),
    ('0001234567891', 2000000, 2000000, 0, NULL, NULL, NOW()),
    ('0001234567892', 3000000, 3000000, 0, NULL, NULL, NOW());

-- interest_accruals: 001 商品 2 口座 × 3 行 + 003 商品 1 口座 × 2 行
INSERT INTO interest_accruals
(business_date, account_number, product_code, principal_jpy, rate, days, accrued_jpy, status, posted_txn_id, created_ts)
VALUES
    ('2026-06-30', '0001234567890', '001', 1000000, 0.0100, 30, 1000, 'AC', NULL, NOW()),
    ('2026-06-29', '0001234567890', '001', 1000000, 0.0100, 29,  966, 'AC', NULL, NOW()),
    ('2026-06-28', '0001234567890', '001', 1000000, 0.0100, 28,  933, 'AC', NULL, NOW()),
    ('2026-06-30', '0001234567891', '001', 2000000, 0.0100, 30, 2000, 'AC', NULL, NOW()),
    ('2026-06-29', '0001234567891', '001', 2000000, 0.0100, 29, 1933, 'AC', NULL, NOW()),
    ('2026-06-28', '0001234567891', '001', 2000000, 0.0100, 28, 1866, 'AC', NULL, NOW()),
    -- 003 商品 (product filter 対象外)
    ('2026-06-30', '0001234567892', '003', 3000000, 0.0200, 30, 6000, 'AC', NULL, NOW()),
    ('2026-06-29', '0001234567892', '003', 3000000, 0.0200, 29, 5800, 'AC', NULL, NOW());
