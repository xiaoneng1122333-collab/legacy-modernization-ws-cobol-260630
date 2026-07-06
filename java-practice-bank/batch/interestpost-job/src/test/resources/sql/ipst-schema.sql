-- Test schema for interestpost-job: V1__initial_schema.sql のテーブルをテスト用に再作成.
-- 既存テーブルがある場合は IF NOT EXISTS でスキップ.

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
CREATE INDEX IF NOT EXISTS idx_txn_bd_acct ON transactions (business_date, account_number);
CREATE INDEX IF NOT EXISTS idx_txn_acct_bd ON transactions (account_number, business_date);

CREATE TABLE IF NOT EXISTS interest_accruals (
    accrual_id      BIGSERIAL    PRIMARY KEY,
    business_date   DATE         NOT NULL,
    account_number  CHAR(13)     NOT NULL,
    product_code    CHAR(3)      NOT NULL,
    principal_jpy   BIGINT       NOT NULL,
    rate            NUMERIC(5,4) NOT NULL,
    days            SMALLINT     NOT NULL,
    accrued_jpy     BIGINT       NOT NULL,
    status          CHAR(2)      NOT NULL,
    posted_txn_id   CHAR(18),
    created_ts      TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT iac_status_enum CHECK (status IN ('AC','PT','CN'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_iac_bd_acct ON interest_accruals (business_date, account_number);
CREATE INDEX IF NOT EXISTS idx_iac_status_bd ON interest_accruals (status, business_date);

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
