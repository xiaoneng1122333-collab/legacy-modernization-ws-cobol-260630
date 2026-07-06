DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
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
CREATE INDEX idx_customers_name_kana ON customers (cust_name_kana);
CREATE INDEX idx_customers_phone     ON customers (phone);

CREATE TABLE audit_log (
    id           SERIAL PRIMARY KEY,
    cust_id      CHAR(10)    NOT NULL,
    event_type   VARCHAR(64) NOT NULL,
    new_status   CHAR(1),
    business_date VARCHAR(8),
    created_at   TIMESTAMP(0) NOT NULL DEFAULT NOW()
);

INSERT INTO customers (cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address)
    VALUES ('0000000001', 'システム',        'システム',          'A', 'S', NULL, NULL);
INSERT INTO customers (cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address)
    VALUES ('0000000002', '田中 太郎',       'タナカ タロウ',      'A', 'G', '03-1234-5678', '東京都中央区1-1');
INSERT INTO customers (cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address)
    VALUES ('0000000003', '田中 次郎',       'タナカ ジロウ',      'A', 'S', '03-1234-5678', '東京都中央区2-2');
INSERT INTO customers (cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address)
    VALUES ('0000000004', '鈴木 花子',       'スズキ ハナコ',      'A', 'B', '06-9876-5432', '大阪府大阪市3-3');
INSERT INTO customers (cust_id, cust_name, cust_name_kana, cust_status, tier, phone, address)
    VALUES ('0000000005', '佐藤 三郎',       'サトウ サブロウ',    'B', 'B', '052-555-6666', '愛知県名古屋市4-4');
