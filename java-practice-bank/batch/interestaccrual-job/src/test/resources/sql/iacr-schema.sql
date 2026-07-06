-- Test fixture for interestaccrual-job: create balances + interest_accruals tables.
-- (実スキーマ V1__initial_schema.sql と同一内容)
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
