-- operations-job テスト用フィクスチャ
-- OPS-FINALIZE / OPS-SEED-SYSTEM-ACCOUNTS 動作確認

-- batch_run: 開始前の状態は空
-- customers 初期状態: 1 件のみ存在 (再テスト時の冪等性検証)
INSERT INTO customers (cust_id, cust_name, cust_name_kana, cust_status, tier)
VALUES ('9999999999', '既存顧客', 'キソンコキャク', 'A', 'B')
ON CONFLICT (cust_id) DO NOTHING;

-- OPS-FINALIZE 対象: status='PT' のトランザクション
INSERT INTO transactions (txn_id, business_date, source_batch_id, account_number, amount_jpy, status, created_ts)
VALUES
('TXN20260706000001', '2026-07-06', 'BATCH20260706', '0001234567890', 1000, 'PT', NOW()),
('TXN20260706000002', '2026-07-06', 'BATCH20260706', '0001234567890', 2000, 'PT', NOW()),
('TXN20260706000003', '2026-07-06', 'BATCH20260706', '0009876543210', 3000, 'PT', NOW());

-- batch_run の事前投入は行わない (batch-run-start Step で INSERT を検証するため)
