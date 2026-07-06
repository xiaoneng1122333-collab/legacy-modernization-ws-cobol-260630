-- Test fixture for txnvalidate-job: create transactions table + extend status enum.
-- (実スキーマ V1__initial_schema.sql と同一内容 + VO/RJ 追加)
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
    -- txn_amount_positive / txn_currency_jpy は Phase2 バリデーションロジック側で担保.
    -- (E009/E010/E013 をテストするため CHECK 制約は落とす)
    CONSTRAINT txn_status_enum     CHECK (status IN ('PT','SE','RV','VO','RJ')),
    CONSTRAINT txn_reversal_pair   CHECK ((status = 'RV') = (reversal_of IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_txn_source_batch_seq ON transactions (source_batch_id, source_seq);
CREATE INDEX IF NOT EXISTS idx_txn_bd_acct ON transactions (business_date, account_number);
CREATE INDEX IF NOT EXISTS idx_txn_acct_bd ON transactions (account_number, business_date);

-- 拒否理由保存テーブル (バリデーション拒否コードをトランザクション単位で保存)
CREATE TABLE IF NOT EXISTS txn_validation_reject (
    reject_id       BIGSERIAL PRIMARY KEY,
    txn_id          CHAR(18) NOT NULL REFERENCES transactions(txn_id),
    error_code      VARCHAR(8) NOT NULL,
    business_date   DATE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tv_reject_txn ON txn_validation_reject(txn_id);
