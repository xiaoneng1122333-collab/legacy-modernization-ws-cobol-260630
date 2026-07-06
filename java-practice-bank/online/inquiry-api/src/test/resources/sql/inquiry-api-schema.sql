-- Schema for inquiry-api integration tests
CREATE TABLE IF NOT EXISTS customers (
    cust_id          CHAR(10)     PRIMARY KEY,
    cust_name        VARCHAR(60)  NOT NULL,
    cust_name_kana   VARCHAR(80)  NOT NULL,
    cust_status      CHAR(1)      NOT NULL,
    tier             CHAR(1)      NOT NULL DEFAULT 'B',
    phone            VARCHAR(20),
    address          VARCHAR(120),
    created_at       TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(0) NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS accounts (
    acct_number      CHAR(13)     PRIMARY KEY,
    acct_name        VARCHAR(60)  NOT NULL,
    branch_code      CHAR(3)      NOT NULL,
    product_code     CHAR(3)      NOT NULL,
    acct_status      CHAR(1)      NOT NULL,
    cust_id          CHAR(10)     NOT NULL,
    opened_date      DATE         NOT NULL,
    dormancy_date    DATE,
    created_at       TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(0) NOT NULL DEFAULT NOW()
);
