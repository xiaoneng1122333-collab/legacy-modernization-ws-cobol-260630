DROP TABLE IF EXISTS fee_schedules;
CREATE TABLE fee_schedules (
    category         CHAR(2)      NOT NULL,
    tier             CHAR(1)      NOT NULL,
    effective_date   DATE         NOT NULL,
    fee_jpy          BIGINT       NOT NULL,
    created_at       TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    PRIMARY KEY (category, tier, effective_date)
);

-- 12 rows of seed data matching COBOL feeschedules-mvp.dat
-- カテゴリ 10=入金
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('10', '1', '2026-01-01', 110);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('10', '2', '2026-01-01', 220);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('10', '3', '2026-01-01', 330);
-- カテゴリ 20=出金
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('20', '1', '2026-01-01', 110);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('20', '2', '2026-01-01', 220);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('20', '3', '2026-01-01', 330);
-- カテゴリ 30=振込
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('30', '1', '2026-01-01', 110);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('30', '2', '2026-01-01', 220);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('30', '3', '2026-01-01', 330);
-- カテゴリ 40=海外送金 (tier1 非課金, tier3 年度跨ぎで金額変動)
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('40', '1', '2026-01-01', 0);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('40', '3', '2026-01-01', 880);
INSERT INTO fee_schedules (category, tier, effective_date, fee_jpy) VALUES ('40', '3', '2027-01-01', 968);
