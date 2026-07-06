-- operations-job テスト用スキーマ
-- audit_log: OPS 監査テーブル (22-operations / 03-customer 統合管理)
DROP TABLE IF EXISTS audit_log CASCADE;
CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    batch_id     VARCHAR(20),
    event_type   VARCHAR(64) NOT NULL,
    step_name    VARCHAR(64),
    business_date VARCHAR(8),
    created_ts   TIMESTAMP(0) NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_batch ON audit_log (batch_id);

-- batch_run: OPS-BATCH-RUN-START / COMPLETE 対象テーブル
DROP TABLE IF EXISTS batch_run CASCADE;
CREATE TABLE batch_run (
    batch_id        VARCHAR(20) NOT NULL PRIMARY KEY,
    business_date   VARCHAR(8),
    started_ts      TIMESTAMP(0),
    completed_ts    TIMESTAMP(0),
    status          VARCHAR(4) NOT NULL DEFAULT 'RN',
    current_step    VARCHAR(64),
    txns_posted     INTEGER NOT NULL DEFAULT 0,
    errors_count    INTEGER NOT NULL DEFAULT 0
);

-- customers / accounts / balances: OPS-SEED-SYSTEM-ACCOUNTS が UPSERT 対象
DROP TABLE IF EXISTS customers CASCADE;
CREATE TABLE customers (
    cust_id        CHAR(10)     NOT NULL PRIMARY KEY,
    cust_name      VARCHAR(60)  NOT NULL,
    cust_name_kana VARCHAR(80)  NOT NULL,
    cust_status    CHAR(1)      NOT NULL,
    tier           CHAR(1)      NOT NULL DEFAULT 'B',
    phone          VARCHAR(20),
    address        VARCHAR(120),
    created_at     TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP(0) NOT NULL DEFAULT NOW()
);

DROP TABLE IF EXISTS accounts CASCADE;
CREATE TABLE accounts (
    account_number CHAR(13)    NOT NULL PRIMARY KEY,
    account_name   VARCHAR(60) NOT NULL,
    status         CHAR(2)     NOT NULL DEFAULT 'AC',
    product_code   VARCHAR(8)
);

DROP TABLE IF EXISTS balances CASCADE;
CREATE TABLE balances (
    account_number CHAR(13)    NOT NULL PRIMARY KEY REFERENCES accounts(account_number),
    balance_jpy    BIGINT      NOT NULL DEFAULT 0
);

-- transactions: OPS-FINALIZE 対象
DROP TABLE IF EXISTS transactions CASCADE;
CREATE TABLE transactions (
    txn_id           CHAR(18)     NOT NULL PRIMARY KEY,
    business_date    DATE         NOT NULL,
    source_batch_id  VARCHAR(20)  NOT NULL,
    account_number   CHAR(13)     NOT NULL,
    amount_jpy       BIGINT       NOT NULL,
    status           CHAR(2)      NOT NULL,
    created_ts       TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT txn_status_enum CHECK (status IN ('PT','SE','VO','RJ'))
);
CREATE INDEX idx_txn_batch ON transactions (source_batch_id);
