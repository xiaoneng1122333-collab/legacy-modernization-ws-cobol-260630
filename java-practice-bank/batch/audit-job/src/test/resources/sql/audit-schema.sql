-- Test fixture for audit-job: audit_log テーブル.
-- COBOL 設計 audit-*-bd.md 正常系・異常系相当

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
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_log (action);
CREATE INDEX IF NOT EXISTS idx_audit_severity ON audit_log (severity);
