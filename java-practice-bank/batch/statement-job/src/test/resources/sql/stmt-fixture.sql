-- Test fixture for statement-job: STMT-GENERATE-BATCH 正常系.
-- 前提: V1__initial_schema.sql のテーブルが存在すること.

TRUNCATE customers, branches, accounts, balances, transactions CASCADE;

-- customers (5 rows)
INSERT INTO customers (cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address)
VALUES
    ('CUST000001', '田中 太郎', 'タナカ タロウ', 'A', 'G', '03-1111-0001', '東京都千代田区1-1'),
    ('CUST000002', '鈴木 花子', 'スズキ ハナコ', 'A', 'S', '06-2222-0002', '大阪府大阪市2-2'),
    ('CUST000003', '佐藤 次郎', 'サトウ ジロウ', 'A', 'B', '052-3333-0003', '愛知県名古屋市3-3'),
    ('CUST000004', '高橋 三郎', 'タカハシ サブロウ', 'A', 'B', '092-4444-0004', '福岡県福岡市4-4'),
    ('CUST000005', '渡辺 四郎', 'ワタナベ シロウ', 'A', 'S', '03-5555-0005', '東京都新宿区5-5');

-- branches (3 rows)
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone)
VALUES
    ('001', '東京本店', 'トウキョウホンテン', 'H', '東京都千代田区丸の内1-1', '03-0001-0001'),
    ('002', '新宿支店', 'シンジュク',         'B', '東京都新宿区西新宿2-2',   '03-0002-0002'),
    ('005', '大阪本店', 'オオサカホンテン',    'H', '大阪府大阪市北区梅田5-5', '06-0005-0005');

-- accounts (5 rows, status='A')
INSERT INTO accounts (acct_number, acct_name, branch_code, product_code, acct_status, cust_id, opened_date)
VALUES
    ('0010010000001', '田中 太郎',   '001', '001', 'A', 'CUST000001', '2026-01-01'),
    ('0020010000002', '鈴木 花子',   '002', '001', 'A', 'CUST000002', '2026-01-01'),
    ('0050010000005', '佐藤 次郎',   '005', '001', 'A', 'CUST000003', '2026-01-01'),
    ('0010010000006', '高橋 三郎',   '001', '002', 'A', 'CUST000004', '2026-01-01'),
    ('0020010000007', '渡辺 四郎',   '002', '001', 'A', 'CUST000005', '2026-01-01');

-- balances (5 rows, all accts have positive balance)
INSERT INTO balances (account_number, balance_jpy, available_jpy, hold_jpy, last_business_date)
VALUES
    ('0010010000001', 1000000, 1000000, 0, '2026-06-13'),
    ('0020010000002', 2000000, 2000000, 0, '2026-06-13'),
    ('0050010000005', 3000000, 3000000, 0, '2026-06-13'),
    ('0010010000006', 4000000, 4000000, 0, '2026-06-13'),
    ('0020010000007', 5000000, 5000000, 0, '2026-06-13');

-- transactions (3 per account, various amounts)
INSERT INTO transactions
    (txn_id, business_date, system_ts, category, account_number,
     counter_account_number, amount_jpy, currency, description,
     source_system, source_batch_id, source_seq, status, reversal_of, created_by, created_ts)
VALUES
    ('STMT-TX-20260601A', '2026-06-10', NOW(), '20', '0010010000001',
     '9999999999999',  50000, 'JPY', 'deposit',  'BATCH', 'STMT-TEST-001', 1, 'PT', NULL, 'test-stmt', NOW()),
    ('STMT-TX-20260602A', '2026-06-11', NOW(), '20', '0010010000001',
     '9999999999999', 150000, 'JPY', 'deposit',  'BATCH', 'STMT-TEST-001', 2, 'PT', NULL, 'test-stmt', NOW()),
    ('STMT-TX-20260603A', '2026-06-12', NOW(), '10', '0010010000001',
     '9999999999999',  30000, 'JPY', 'withdraw', 'BATCH', 'STMT-TEST-001', 3, 'PT', NULL, 'test-stmt', NOW()),

    ('STMT-TX-20260601B', '2026-06-10', NOW(), '20', '0020010000002',
     '9999999999999', 100000, 'JPY', 'deposit',  'BATCH', 'STMT-TEST-001', 4, 'PT', NULL, 'test-stmt', NOW()),
    ('STMT-TX-20260602B', '2026-06-11', NOW(), '10', '0020010000002',
     '9999999999999',  50000, 'JPY', 'withdraw', 'BATCH', 'STMT-TEST-001', 5, 'PT', NULL, 'test-stmt', NOW()),
    ('STMT-TX-20260603B', '2026-06-12', NOW(), '30', '0020010000002',
     '9999999999999', 200000, 'JPY', 'transfer','BATCH', 'STMT-TEST-001', 6, 'PT', NULL, 'test-stmt', NOW()),

    ('STMT-TX-20260601C', '2026-06-10', NOW(), '20', '0050010000005',
     '9999999999999', 200000, 'JPY', 'deposit',  'BATCH', 'STMT-TEST-001', 7, 'PT', NULL, 'test-stmt', NOW()),
    ('STMT-TX-20260602C', '2026-06-11', NOW(), '20', '0050010000005',
     '9999999999999', 200000, 'JPY', 'deposit',  'BATCH', 'STMT-TEST-001', 8, 'PT', NULL, 'test-stmt', NOW()),
    ('STMT-TX-20260603C', '2026-06-12', NOW(), '10', '0050010000005',
     '9999999999999', 100000, 'JPY', 'withdraw', 'BATCH', 'STMT-TEST-001', 9, 'PT', NULL, 'test-stmt', NOW()),

    ('STMT-TX-20260601D', '2026-06-10', NOW(), '20', '0010010000006',
     '9999999999999', 300000, 'JPY', 'deposit',  'BATCH', 'STMT-TEST-001', 10, 'PT', NULL, 'test-stmt', NOW()),
    ('STMT-TX-20260602D', '2026-06-11', NOW(), '30', '0010010000006',
     '9999999999999', 100000, 'JPY', 'transfer', 'BATCH', 'STMT-TEST-001', 11, 'PT', NULL, 'test-stmt', NOW()),
    ('STMT-TX-20260603D', '2026-06-12', NOW(), '40', '0010010000006',
     '9999999999999', 150000, 'JPY', 'overseas', 'BATCH', 'STMT-TEST-001', 12, 'PT', NULL, 'test-stmt', NOW()),

    ('STMT-TX-20260601E', '2026-06-10', NOW(), '20', '0020010000007',
     '9999999999999', 500000, 'JPY', 'deposit',  'BATCH', 'STMT-TEST-001', 13, 'PT', NULL, 'test-stmt', NOW()),
    ('STMT-TX-20260602E', '2026-06-11', NOW(), '10', '0020010000007',
     '9999999999999', 100000, 'JPY', 'withdraw', 'BATCH', 'STMT-TEST-001', 14, 'PT', NULL, 'test-stmt', NOW()),
    ('STMT-TX-20260603E', '2026-06-12', NOW(), '20', '0020010000007',
     '9999999999999', 200000, 'JPY', 'deposit',  'BATCH', 'STMT-TEST-001', 15, 'PT', NULL, 'test-stmt', NOW());
