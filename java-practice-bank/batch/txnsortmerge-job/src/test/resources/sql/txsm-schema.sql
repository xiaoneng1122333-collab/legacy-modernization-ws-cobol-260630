-- Test fixture for txnsortmerge-job: extend transactions table + staging/recon/ready/error tables.
-- Extends V1__initial_schema.sql with SE/MG statuses and sorting/merge staging tables.
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
    CONSTRAINT txsm_txn_status_enum     CHECK (status IN ('PT','SE','RV','VO','RJ','MG')),
    CONSTRAINT txsm_txn_reversal_pair   CHECK ((status = 'RV') = (reversal_of IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_txsm_txn_source_batch_seq ON transactions (source_batch_id, source_seq);

-- TXSM-SORT-BATCH 出力 (ソート済み明細)
CREATE TABLE IF NOT EXISTS txn_sorted_txn (
    txn_id          CHAR(18)    NOT NULL PRIMARY KEY,
    account_number  CHAR(13)    NOT NULL,
    source_seq      INTEGER     NOT NULL,
    amount_jpy      NUMERIC(20) NOT NULL,
    sorted_seq      BIGINT      NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_txsm_sorted ON txn_sorted_txn (sorted_seq);

-- 前日取引 (RECON) 入力
CREATE TABLE IF NOT EXISTS txn_recon_prev (
    txn_id          CHAR(18)    NOT NULL PRIMARY KEY,
    account_number  CHAR(13)    NOT NULL,
    source_seq      INTEGER     NOT NULL,
    amount_jpy      NUMERIC(20) NOT NULL
);

-- TXSM-MERGE-BATCH 出力 (マージ済み明細)
CREATE TABLE IF NOT EXISTS txn_ready_txn (
    txn_id_source   CHAR(18)    NOT NULL,
    source_kind     VARCHAR(10) NOT NULL,  -- SORTED / RECON
    account_number  CHAR(13)    NOT NULL,
    source_seq      INTEGER     NOT NULL,
    amount_jpy      NUMERIC(20) NOT NULL
);

-- TXSM-MERGE-BATCH 重複エラー退避
CREATE TABLE IF NOT EXISTS txn_error_record (
    error_id        BIGSERIAL   PRIMARY KEY,
    txn_id          CHAR(18)    NOT NULL,
    account_number  CHAR(13)    NOT NULL,
    source_seq      INTEGER     NOT NULL,
    amount_jpy      NUMERIC(20) NOT NULL,
    error_code      VARCHAR(8)  NOT NULL,
    source_kind     VARCHAR(10) NOT NULL  -- SORTED / RECON
);
