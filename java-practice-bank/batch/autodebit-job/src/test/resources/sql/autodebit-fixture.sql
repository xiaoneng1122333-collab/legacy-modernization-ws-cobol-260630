-- Test fixture: autodebit_schedules + balances にバリデーション対象データ
-- COBOL 設計 ad-run-daily-bd.md 正常系 #1, #2 と異常系 #3, #4 相当
TRUNCATE autodebit_schedules, balances, transactions CASCADE;

-- 残高十分な口座 (AC2001 の引き落とし元)
INSERT INTO balances
(account_number, balance_jpy, available_jpy, hold_jpy, last_txn_id, last_business_date, updated_ts)
VALUES
('0001234567890', 100000, 100000, 0, NULL, NULL, NOW());

-- 正常: 残高 >= 金額 (POST 成功相当)
INSERT INTO autodebit_schedules
(instruction_id, payer_account, payee_name, amount_jpy, frequency, next_due_date,
 status, last_attempt_date, last_attempt_result, consecutive_failures, created_ts, updated_ts)
VALUES
('AD2001', '0001234567890', 'Utility Co', 5000, 'M', '2026-07-06',
 'AC', NULL, NULL, 0, NOW(), NOW());

-- 異常: 残高不足 (NF 判定)
INSERT INTO autodebit_schedules
(instruction_id, payer_account, payee_name, amount_jpy, frequency, next_due_date,
 status, last_attempt_date, last_attempt_result, consecutive_failures, created_ts, updated_ts)
VALUES
('AD2002', '0001234567890', 'Big Corp', 999999, 'M', '2026-07-06',
 'AC', NULL, NULL, 0, NOW(), NOW());

-- 異常: 口座未発見 (CL 判定 → TM に遷移)
INSERT INTO autodebit_schedules
(instruction_id, payer_account, payee_name, amount_jpy, frequency, next_due_date,
 status, last_attempt_date, last_attempt_result, consecutive_failures, created_ts, updated_ts)
VALUES
('AD2003', '0009999999999', 'Ghost Payee', 1000, 'M', '2026-07-06',
 'AC', NULL, NULL, 0, NOW(), NOW());

-- 期限未到来 (処理対象外)
INSERT INTO autodebit_schedules
(instruction_id, payer_account, payee_name, amount_jpy, frequency, next_due_date,
 status, last_attempt_date, last_attempt_result, consecutive_failures, created_ts, updated_ts)
VALUES
('AD2004', '0001234567890', 'Future Payee', 2000, 'M', '2026-08-06',
 'AC', NULL, NULL, 0, NOW(), NOW());
