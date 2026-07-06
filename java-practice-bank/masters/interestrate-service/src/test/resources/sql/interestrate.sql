DROP TABLE IF EXISTS interest_rates;
CREATE TABLE interest_rates (
    product_code       CHAR(3)      NOT NULL,
    effective_date     DATE         NOT NULL,
    annual_rate        NUMERIC(7,6) NOT NULL,
    tier_threshold_jpy BIGINT,
    created_at         TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    PRIMARY KEY (product_code, effective_date)
);

-- 6 rows of seed data matching COBOL interestrates-mvp.dat
-- product 001: 普通預金 (2 tiers, 2 effective dates)
INSERT INTO interest_rates (product_code, effective_date, annual_rate, tier_threshold_jpy) VALUES ('001', '2026-01-01', 0.001000, 0);
INSERT INTO interest_rates (product_code, effective_date, annual_rate, tier_threshold_jpy) VALUES ('001', '2027-01-01', 0.002000, 1000000);
-- product 002: 定期預金 (1 tier, 2 effective dates)
INSERT INTO interest_rates (product_code, effective_date, annual_rate, tier_threshold_jpy) VALUES ('002', '2026-01-01', 0.050000, 0);
INSERT INTO interest_rates (product_code, effective_date, annual_rate, tier_threshold_jpy) VALUES ('002', '2027-01-01', 0.055000, 0);
-- product 003: 当座預金 (1 tier, 1 effective date — zero rate)
INSERT INTO interest_rates (product_code, effective_date, annual_rate, tier_threshold_jpy) VALUES ('003', '2026-01-01', 0.000000, NULL);
-- product 004: 外貨預金 (1 tier, 1 effective date)
INSERT INTO interest_rates (product_code, effective_date, annual_rate, tier_threshold_jpy) VALUES ('004', '2026-01-01', 0.010000, 0);
