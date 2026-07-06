CREATE TABLE IF NOT EXISTS m_calendar (
    cal_date     DATE PRIMARY KEY,
    day_type     VARCHAR(1) NOT NULL,
    holiday_name VARCHAR(100)
);

INSERT INTO m_calendar (cal_date, day_type, holiday_name) VALUES
    ('2026-01-01', 'H', '元日'),
    ('2026-01-02', 'B', NULL),
    ('2026-01-03', 'W', NULL)
ON CONFLICT DO NOTHING;
