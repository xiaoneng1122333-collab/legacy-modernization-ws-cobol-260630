-- Test fixture: txnsortmerge-job.
-- COBOL 設計 txsm-sort-batch-bd.md (TC01-TC05) / txsm-merge-batch-bd.md (TC01-TC04) 相当.
-- 全レコードは妥当性検証済み (status=VO) 前提で投入.
TRUNCATE transactions, txn_sorted_txn, txn_recon_prev, txn_ready_txn, txn_error_record CASCADE;

-- ソート対象: 3 件 — 降順に投入して payer-acct → seq 昇順でソートされることを検証.
INSERT INTO transactions
(txn_id, business_date, system_ts, category, account_number, counter_account_number,
 amount_jpy, currency, description, source_system, source_batch_id, source_seq,
 status, reversal_of, created_by, created_ts)
VALUES
('TXN20260706000001', '2026-07-06', '2026-07-06 09:00:00', '10', '0009876543210', NULL,
 3000, 'JPY', 'deposit C', 'TXSM', 'BATCH20260706', 1,
 'VO', NULL, 'txsm-job', NOW()),
('TXN20260706000002', '2026-07-06', '2026-07-06 09:00:01', '10', '0001234567890', NULL,
 2000, 'JPY', 'deposit B', 'TXSM', 'BATCH20260706', 2,
 'VO', NULL, 'txsm-job', NOW()),
('TXN20260706000003', '2026-07-06', '2026-07-06 09:00:02', '10', '0001234567890', NULL,
 1000, 'JPY', 'deposit A', 'TXSM', 'BATCH20260706', 3,
 'VO', NULL, 'txsm-job', NOW());

-- 前日取引 (RECON): 2 件 — sorted と disjoint なキーで交差マージを検証.
INSERT INTO txn_recon_prev (txn_id, account_number, source_seq, amount_jpy) VALUES
('RECON202607050001', '0001111111111', 1, 500),
('RECON202607050002', '0005555555555', 2, 700);
