DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    acct_number      CHAR(13)     NOT NULL PRIMARY KEY,
    acct_name        VARCHAR(60)  NOT NULL,
    branch_code      CHAR(3)      NOT NULL,
    product_code     CHAR(3)      NOT NULL,
    acct_status      CHAR(1)      NOT NULL,
    cust_id          CHAR(10)     NOT NULL,
    opened_date      DATE         NOT NULL DEFAULT CURRENT_DATE,
    dormancy_date    DATE,
    created_at       TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(0) NOT NULL DEFAULT NOW()
);

-- Seed: 5 accounts matching COBOL accounts-mvp.dat
-- acct_number: 0010030000001 (branch 001, product 003, cust 0000000002)
INSERT INTO accounts (acct_number, acct_name, branch_code, product_code, acct_status, cust_id, opened_date, dormancy_date)
VALUES ('0010030000001', '山田太郎', '001', '003', 'A', '0000000002', '2026-01-01', '2026-01-01');

INSERT INTO accounts (acct_number, acct_name, branch_code, product_code, acct_status, cust_id, opened_date, dormancy_date)
VALUES ('0010030000002', '山田花子', '001', '003', 'A', '0000000003', '2026-01-01', '2026-01-01');

INSERT INTO accounts (acct_number, acct_name, branch_code, product_code, acct_status, cust_id, opened_date, dormancy_date)
VALUES ('0040010000003', '鈴木一郎', '004', '001', 'D', '0000000004', '2026-01-01', '2026-01-01');

INSERT INTO accounts (acct_number, acct_name, branch_code, product_code, acct_status, cust_id, opened_date, dormancy_date)
VALUES ('0070010000005', '佐藤次郎', '007', '001', 'A', '0000000005', '2026-01-01', '2026-01-01');

INSERT INTO accounts (acct_number, acct_name, branch_code, product_code, acct_status, cust_id, opened_date, dormancy_date)
VALUES ('0010010000006', '高橋三郎', '001', '001', 'P', '0000000005', '2026-01-01', '2026-01-01');
