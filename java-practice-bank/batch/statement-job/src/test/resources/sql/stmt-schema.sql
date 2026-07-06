-- Test schema for statement-job: accounts / customers / branches / balances / transactions.
-- (実スキーマ V1__initial_schema.sql と同一内容)

CREATE TABLE IF NOT EXISTS customers (
    cust_id          CHAR(10)     NOT NULL PRIMARY KEY,
    cust_name        VARCHAR(60)  NOT NULL,
    cust_name_kana   VARCHAR(80)  NOT NULL,
    cust_status      CHAR(1)      NOT NULL,
    tier             CHAR(1)      NOT NULL DEFAULT 'B',
    phone            VARCHAR(20),
    address          VARCHAR(120),
    created_at       TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(0) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS branches (
    branch_code      CHAR(3)      NOT NULL PRIMARY KEY,
    branch_name      VARCHAR(60)  NOT NULL,
    branch_name_kana VARCHAR(80)  NOT NULL,
    branch_type      CHAR(1)      NOT NULL,
    address          VARCHAR(120),
    phone            VARCHAR(20),
    created_at       TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(0) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS accounts (
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

CREATE TABLE IF NOT EXISTS balances (
    account_number      CHAR(13)     NOT NULL PRIMARY KEY,
    balance_jpy         BIGINT       NOT NULL,
    available_jpy       BIGINT       NOT NULL,
    hold_jpy            BIGINT       NOT NULL DEFAULT 0,
    last_txn_id         CHAR(18),
    last_business_date  DATE,
    updated_ts          TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT bal_hold_nonneg CHECK (hold_jpy >= 0)
);

CREATE TABLE IF NOT EXISTS transactions (
    txn_id                 CHAR(18)     NOT NULL PRIMARY KEY,
    business_date          DATE         NOT NULL,
    system_ts              TIMESTAMP(0) NOT NULL,
    category               CHAR(2)      NOT NULL,
    account_number         CHAR(13)     NOT NULL,
    counter_account_number CHAR(13),
    amount_jpy             BIGINT       NOT NULL,
    currency               CHAR(3)      NOT NULL,
    description            VARCHAR(120),
    source_system          VARCHAR(20)  NOT NULL,
    source_batch_id        CHAR(14)     NOT NULL,
    source_seq             INTEGER      NOT NULL,
    status                 CHAR(2)      NOT NULL,
    reversal_of            CHAR(18),
    created_by             VARCHAR(20)  NOT NULL,
    created_ts             TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT txn_status_enum     CHECK (status IN ('PT','SE','RV')),
    CONSTRAINT txn_reversal_pair   CHECK ((status = 'RV') = (reversal_of IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_txn_source_batch_seq ON transactions (source_batch_id, source_seq);
