-- 同一営業日 (2026-07-06) に COBOL 側 = Java 側で一致するフィクスチャを投入.
-- transactions 5件 / balances 3件 / customers 2件

INSERT INTO transactions (txn_id, business_date, amount_jpy, source_batch_id, status) VALUES
('TXN20260706000001', '2026-07-06', 1000, 'BATCH20260706', 'SE'),
('TXN20260706000002', '2026-07-06', 2000, 'BATCH20260706', 'SE'),
('TXN20260706000003', '2026-07-06', 3000, 'BATCH20260706', 'PT'),
('TXN20260706000004', '2026-07-06', 4000, 'BATCH20260706', 'SE'),
('TXN20260706000005', '2026-07-06', 5000, 'BATCH20260706', 'SE');

INSERT INTO practicebank.transactions (txn_id, business_date, amount_jpy, source_batch_id, status) VALUES
('TXN20260706000001', '2026-07-06', 1000, 'BATCH20260706', 'SE'),
('TXN20260706000002', '2026-07-06', 2000, 'BATCH20260706', 'SE'),
('TXN20260706000003', '2026-07-06', 3000, 'BATCH20260706', 'PT'),
('TXN20260706000004', '2026-07-06', 4000, 'BATCH20260706', 'SE'),
('TXN20260706000005', '2026-07-06', 5000, 'BATCH20260706', 'SE');

INSERT INTO balances (account_number, business_date, balance_jpy) VALUES
('0001234567890', '2026-07-06', 10000),
('0001234567891', '2026-07-06', 20000),
('0009876543210', '2026-07-06', 30000);

INSERT INTO practicebank.balances (account_number, business_date, balance_jpy) VALUES
('0001234567890', '2026-07-06', 10000),
('0001234567891', '2026-07-06', 20000),
('0009876543210', '2026-07-06', 30000);

INSERT INTO customers (cust_id, cust_name, cust_name_kana, cust_status, tier) VALUES
('0000000001', 'システム',     'システム',     'A', 'S'),
('0000000002', '田中 太郎',    'タナカ タロウ', 'A', 'G');

INSERT INTO practicebank.customers (cust_id, cust_name, cust_name_kana, cust_status, tier) VALUES
('0000000001', 'システム',     'システム',     'A', 'S'),
('0000000002', '田中 太郎',    'タナカ タロウ', 'A', 'G');
