-- V8: 口座ライフサイクル (09-accountlifecycle) に必要な列を追加
--
-- 08-account の accounts テーブルは既存だが、ライフサイクル制御に必要な
-- 3 列 (closed_date / overdraft_limit / term_days) が不足しているため、
-- ここで追加する。ALC-OPEN / ALC-CHANGE-STATE / ALC-DORMANCY-SCAN が
-- これらを READ/WRITE する。

ALTER TABLE accounts
    ADD COLUMN closed_date      DATE,
    ADD COLUMN overdraft_limit  NUMERIC(15, 0),
    ADD COLUMN term_days        INTEGER;

-- 解約口座の検索 (CL/FC 遷移後の参照) 用インデックス
CREATE INDEX IF NOT EXISTS idx_accounts_closed_date ON accounts (closed_date);
