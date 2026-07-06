-- 09-accountlifecycle テスト用 accounts テーブル
-- db/migration/V2 + V8 と同一構成 (closed_date / overdraft_limit / term_days を含む)

DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    acct_number      CHAR(13)      NOT NULL PRIMARY KEY,
    cust_id          CHAR(10)      NOT NULL,
    product_code     CHAR(3)       NOT NULL,
    branch_code      CHAR(3)       NOT NULL,
    acct_status      CHAR(1)       NOT NULL,
    opened_date      DATE          NOT NULL DEFAULT CURRENT_DATE,
    closed_date      DATE,
    overdraft_limit  NUMERIC(15, 0),
    term_days        INTEGER,
    dormancy_date    DATE,
    created_at       TIMESTAMP(0)  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(0)  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_accounts_cust   ON accounts (cust_id);
CREATE INDEX IF NOT EXISTS idx_accounts_branch ON accounts (branch_code);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts (acct_status);

-- ── シードデータ ──────────────────────────────────────────────────────────
-- 口座番号採番スキーム: branch(3) + product(3) + serial(7), serial >= 9000000
--
--   Pending (P) 口座: 3 件
--   Active  (A) 口座: 5 件 (休眠基準日内 3 件 / 超過 2 件)
--   Suspended (S): 1 件
--   Closed  (C) 口座: 1 件

INSERT INTO accounts (acct_number, cust_id,    product_code, branch_code, acct_status, opened_date, closed_date, overdraft_limit, term_days, dormancy_date) VALUES
-- Pending (新規開設済み直後)
('0010009000000', '0000000099', '000', '001', 'P', '2026-06-01', NULL,        0,      0,     NULL),
('0010009000001', '0000000100', '000', '001', 'P', '2026-06-02', NULL,        100000, 365,   NULL),
('0020009000000', '0000000101', '000', '002', 'P', '2026-06-03', NULL,        0,      0,     NULL),

-- Active — 休眠基準日超過 (BusinessDate=2029-06-01, 基準日=2027-06-02)
-- これらは 2026-06-01 以前の dormancy_date を持つ -> 移行対象
('0010009000010', '0000000200', '000', '001', 'A', '2024-01-01', NULL,        500000, 730,   '2026-06-01'),
('0010009000011', '0000000201', '000', '001', 'A', '2024-01-01', NULL,        500000, 730,   '2025-01-01'),

-- Active — 休眠基準日以内 (移行対象外)
('0010009000012', '0000000202', '000', '001', 'A', '2024-01-01', NULL,        500000, 730,   '2028-01-01'),
('0010009000013', '0000000203', '000', '001', 'A', '2024-01-01', NULL,        500000, 730,   '2027-12-31'),
('0020009000010', '0000000204', '000', '002', 'A', '2024-01-01', NULL,        500000, 730,   '2028-06-01'),

-- Suspended (休止)
('0010009000020', '0000000300', '000', '001', 'S', '2024-01-01', NULL,        500000, 730,   '2028-01-01'),

-- Closed (解約済)
('0010009000030', '0000000400', '000', '001', 'C', '2024-01-01', '2026-01-01', 0,     0,     NULL);
