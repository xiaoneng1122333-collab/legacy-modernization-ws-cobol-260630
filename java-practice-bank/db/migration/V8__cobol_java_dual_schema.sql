-- V8__cobol_java_dual_schema.sql
-- COBOL と Java の並行稼働期間中、データを論理分離するスキーマを作成する。
-- 既存の public スキーマに定義されたテーブルに対して、COBOL と Java それぞれが
-- 別スキーマに書き込めるようビュー/同義語を用意する。

CREATE SCHEMA IF NOT EXISTS cob;
CREATE SCHEMA IF NOT EXISTS jav;
CREATE SCHEMA IF NOT EXISTS shared;

-- cob スキーマ : COBOL が書き込む先 (COBOL のファイルをそのままマッピング)
-- java スキーマ: Java が書き込む先 (Spring Batch / MyBatis が操作)
-- shared スキーマ: 並行稼働後に統合されるテマスタ (calendar など読み取り専用)

-- 業務データ (トランザクション系) を COBOL / Java で分離
CREATE TABLE cob.transactions (LIKE public.transactions INCLUDING ALL);
CREATE TABLE cob.postings (LIKE public.postings INCLUDING ALL);
CREATE TABLE cob.balances (LIKE public.balances INCLUDING ALL);
CREATE TABLE cob.interest_accruals (LIKE public.interest_accruals INCLUDING ALL);
CREATE TABLE cob.audit_log (LIKE public.audit_log INCLUDING ALL);

CREATE TABLE jav.transactions (LIKE public.transactions INCLUDING ALL);
CREATE TABLE jav.postings (LIKE public.postings INCLUDING ALL);
CREATE TABLE jav.balances (LIKE public.balances INCLUDING ALL);
CREATE TABLE jav.interest_accruals (LIKE public.interest_accruals INCLUDING ALL);
CREATE TABLE jav.audit_log (LIKE public.audit_log INCLUDING ALL);

-- マスタデータは shared スキーマに配置 (ISAM → RDS 移行時に共有)
-- マスタテーブルは ISAM から移行される (Phase 1 の isam-to-rds-job が書き込み)
CREATE TABLE shared.accounts (LIKE public.accounts INCLUDING ALL);
CREATE TABLE shared.customers (LIKE public.customers INCLUDING ALL);
CREATE TABLE shared.branches (LIKE public.branches INCLUDING ALL);
CREATE TABLE shared.products (LIKE public.products INCLUDING ALL);
CREATE TABLE shared.calendar (LIKE public.calendar INCLUDING ALL);
CREATE TABLE shared.interest_rates (LIKE public.interest_rates INCLUDING ALL);
CREATE TABLE shared.fee_schedules (LIKE public.fee_schedules INCLUDING ALL);

-- 並行稼働中の COBOL ↔ Java 相互参照用ビュー (必要に応じて追加)
-- CREATE VIEW cob.accounts     AS SELECT * FROM shared.accounts;
-- CREATE VIEW cob.customers   AS SELECT * FROM shared.customers;
-- CREATE VIEW jav.transactions AS SELECT * FROM cob.transactions; -- COBOL が書いたものも読む場合
