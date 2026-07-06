-- Test fixture for integrationout-job: autodebit_failed_queue / audit_log テーブル.
-- COBOL 設計 into-drain-queue-bd.md / into-publish-event-bd.md 正常系・異常系相当

CREATE TABLE IF NOT EXISTS autodebit_failed_queue (
    queue_id        CHAR(20)     NOT NULL PRIMARY KEY,
    txn_id          CHAR(18)     NOT NULL,
    account_number  CHAR(13)     NOT NULL,
    amount_jpy      BIGINT       NOT NULL,
    failure_reason  CHAR(10)     NOT NULL,
    created_ts      TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    drained_ts      TIMESTAMP(0),
    status          CHAR(2)      NOT NULL DEFAULT 'PD',
    CONSTRAINT adq_status_enum CHECK (status IN ('PD','OK','FL'))
);
CREATE INDEX IF NOT EXISTS idx_adq_status ON autodebit_failed_queue (status);

CREATE TABLE IF NOT EXISTS audit_log (
    audit_id        CHAR(36)     NOT NULL PRIMARY KEY,
    business_date   CHAR(8)      NOT NULL,
    subsystem       VARCHAR(30)  NOT NULL,
    action          VARCHAR(50)  NOT NULL,
    severity        CHAR(1)      NOT NULL,
    account_number  CHAR(13),
    payload         TEXT,
    source_system   VARCHAR(20),
    operator_user   VARCHAR(30),
    created_ts      TIMESTAMP(0) NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_bdate ON audit_log (business_date);
CREATE INDEX IF NOT EXISTS idx_audit_subsys ON audit_log (subsystem);
