-- Test fixture for txnpost-job: create transactions / postings / balances / audit_log tables.
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
    CONSTRAINT txn_amount_positive CHECK (amount_jpy > 0),
    CONSTRAINT txn_currency_jpy    CHECK (currency = 'JPY'),
    CONSTRAINT txn_status_enum     CHECK (status IN ('PT','SE','RV')),
    CONSTRAINT txn_reversal_pair   CHECK ((status = 'RV') = (reversal_of IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS postings (
    posting_id      CHAR(20)     NOT NULL PRIMARY KEY,
    txn_id          CHAR(18)     NOT NULL,
    line_no         SMALLINT     NOT NULL,
    account_number  CHAR(13)     NOT NULL,
    debit_jpy       BIGINT       NOT NULL DEFAULT 0,
    credit_jpy      BIGINT       NOT NULL DEFAULT 0,
    posting_role    CHAR(2)      NOT NULL,
    business_date   DATE         NOT NULL,
    created_ts      TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT pst_amounts_nonneg CHECK (debit_jpy >= 0 AND credit_jpy >= 0),
    CONSTRAINT pst_dr_xor_cr      CHECK ((debit_jpy = 0) <> (credit_jpy = 0)),
    CONSTRAINT pst_role_enum      CHECK (posting_role IN ('DR','CR'))
);

CREATE TABLE IF NOT EXISTS audit_log (
    audit_id        BIGSERIAL    PRIMARY KEY,
    business_date   DATE         NOT NULL,
    system_ts       TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    subsystem       VARCHAR(30)  NOT NULL,
    action          VARCHAR(50)  NOT NULL,
    actor           VARCHAR(30)  NOT NULL,
    target_type     VARCHAR(20)  NOT NULL,
    target_id       VARCHAR(20)  NOT NULL,
    payload_json    JSONB,
    severity        CHAR(1)      NOT NULL,
    schema_version  VARCHAR(10)  NOT NULL DEFAULT '1.0'
);
