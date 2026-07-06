-- Fixture for inquiry-api integration tests
TRUNCATE customers, accounts CASCADE;

INSERT INTO customers (cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address)
VALUES ('0000000002', '田中 太郎', 'タナカ タロウ', 'A', 'G', '03-1234-5678', '東京都中央区1-1');

INSERT INTO accounts (acct_number, acct_name, branch_code, product_code, acct_status, cust_id, opened_date, dormancy_date)
VALUES ('0010030000001', '山田太郎', '001', '003', 'A', '0000000002', '2026-01-01', '2026-01-01');

INSERT INTO accounts (acct_number, acct_name, branch_code, product_code, acct_status, cust_id, opened_date, dormancy_date)
VALUES ('0010010099502', 'テスト花子', '001', '001', 'A', '0000000003', '2025-04-01', null);
