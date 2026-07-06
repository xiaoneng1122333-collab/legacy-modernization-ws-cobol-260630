-- Test fixture for autodebit-job: autodebit_schedules / balances テーブル.
-- (実スキーマ V1__initial_schema.sql と同一内容 — transactions CHECK 制約を PT/SE/RV のみ残す)
CREATE TABLE IF NOT EXISTS autodebit_schedules (
    instruction_id        CHAR(20)     NOT NULL PRIMARY KEY,
    payer_account         CHAR(13)     NOT NULL,
    payee_name            VARCHAR(80)  NOT NULL,
    amount_jpy            BIGINT       NOT NULL,
    frequency             CHAR(1)      NOT NULL,
    next_due_date         DATE         NOT NULL,
    status                CHAR(2)      NOT NULL,
    last_attempt_date     DATE,
    last_attempt_result   CHAR(2),
    consecutive_failures  SMALLINT     NOT NULL DEFAULT 0,
    created_ts            TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_ts            TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT ad_frequency_enum CHECK (frequency IN ('M','W','D')),
    CONSTRAINT ad_status_enum    CHECK (status IN ('AC','SP','TM'))
);
CREATE INDEX IF NOT EXISTS idx_ad_status_due ON autodebit_schedules (status, next_due_date);
CREATE INDEX IF NOT EXISTS idx_ad_payer ON autodebit_schedules (payer_account);

CREATE TABLE IF NOT EXISTS balances (
    account_number      CHAR(13)     NOT NULL PRIMARY KEY,
    balance_jpy         BIGINT       NOT NULL,
    available_jpy       BIGINT       NOT NULL,
    hold_jpy            BIGINT       NOT NULL DEFAULT 0,
    last_txn_id         CHAR(18),
    last_business_date  DATE,
    updated_ts          TIMESTAMP(0) NOT NULL DEFAULT NOW()
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
CREATE INDEX IF NOT EXISTS idx_txn_bd_acct ON transactions (business_date, account_number);
CREATE INDEX IF NOT EXISTS idx_txn_acct_bd ON transactions (account_number, business_date);
CREATE INDEX IF NOT EXISTS idx_txn_src ON transactions (source_system, source_batch_id);
