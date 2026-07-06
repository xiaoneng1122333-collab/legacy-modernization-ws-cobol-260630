DROP TABLE IF EXISTS m_products;
CREATE TABLE m_products (
    product_code      CHAR(3)      PRIMARY KEY,
    product_name      VARCHAR(60)  NOT NULL,
    product_type      CHAR(1)      NOT NULL,
    interest_eligible CHAR(1)      NOT NULL DEFAULT 'Y',
    fee_eligible      CHAR(1)      NOT NULL DEFAULT 'Y',
    min_balance_jpy   BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP(0) NOT NULL DEFAULT NOW()
);

INSERT INTO m_products (product_code, product_name, product_type, interest_eligible, fee_eligible, min_balance_jpy)
VALUES ('001', '普通預金', 'S', 'Y', 'Y', 0);
INSERT INTO m_products (product_code, product_name, product_type, interest_eligible, fee_eligible, min_balance_jpy)
VALUES ('002', '定期預金', 'T', 'Y', 'N', 100000);
INSERT INTO m_products (product_code, product_name, product_type, interest_eligible, fee_eligible, min_balance_jpy)
VALUES ('003', '当座預金', 'C', 'N', 'Y', 0);
