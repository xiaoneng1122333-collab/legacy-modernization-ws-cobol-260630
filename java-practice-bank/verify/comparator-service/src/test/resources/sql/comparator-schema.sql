-- comparator-service テスト用スキーマ
-- Phase 2 では同一 DB/SQLite の別スキーマ運用だが,
-- Testcontainers 環境では同一 schema (public) で投入する.
-- 将来的に practicebank schema にも同テーブルを作成し, 完全な相互比較ができるようにする.

-- public 側 (COBOL 側)
DROP TABLE IF EXISTS transactions CASCADE;
CREATE TABLE transactions (
    txn_id          CHAR(18)     NOT NULL PRIMARY KEY,
    business_date   DATE         NOT NULL,
    amount_jpy      BIGINT       NOT NULL,
    source_batch_id VARCHAR(20),
    status          CHAR(2)      NOT NULL DEFAULT 'PT'
);

DROP TABLE IF EXISTS balances CASCADE;
CREATE TABLE balances (
    account_number  CHAR(13) NOT NULL PRIMARY KEY,
    business_date   DATE,
    balance_jpy     BIGINT   NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS customers CASCADE;
CREATE TABLE customers (
    cust_id        CHAR(10) NOT NULL PRIMARY KEY,
    cust_name      VARCHAR(60) NOT NULL,
    cust_name_kana VARCHAR(80),
    cust_status    CHAR(1) NOT NULL DEFAULT 'A',
    tier           CHAR(1) NOT NULL DEFAULT 'B'
);

-- practicebank スキーマ整備
CREATE SCHEMA IF NOT EXISTS practicebank;

-- practicebank 側 (Java 側). 同テーブル定義. Phase 2 では同一 DB 同一内容を想定.
DROP TABLE IF EXISTS practicebank.transactions CASCADE;
CREATE TABLE practicebank.transactions (
    txn_id          CHAR(18)     NOT NULL PRIMARY KEY,
    business_date   DATE         NOT NULL,
    amount_jpy      BIGINT       NOT NULL,
    source_batch_id VARCHAR(20),
    status          CHAR(2)      NOT NULL DEFAULT 'PT'
);

DROP TABLE IF EXISTS practicebank.balances CASCADE;
CREATE TABLE practicebank.balances (
    account_number  CHAR(13) NOT NULL PRIMARY KEY,
    business_date   DATE,
    balance_jpy     BIGINT   NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS practicebank.customers CASCADE;
CREATE TABLE practicebank.customers (
    cust_id        CHAR(10) NOT NULL PRIMARY KEY,
    cust_name      VARCHAR(60) NOT NULL,
    cust_name_kana VARCHAR(80),
    cust_status    CHAR(1) NOT NULL DEFAULT 'A',
    tier           CHAR(1) NOT NULL DEFAULT 'B'
);
