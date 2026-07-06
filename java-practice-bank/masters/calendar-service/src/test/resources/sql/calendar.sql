CREATE TABLE IF NOT EXISTS m_calendar (
    cal_date     DATE         PRIMARY KEY,
    day_type     CHAR(1)      NOT NULL,
    holiday_name VARCHAR(60)
);

INSERT INTO m_calendar (cal_date, day_type, holiday_name) VALUES ('2026-01-01', 'H', '元日');
INSERT INTO m_calendar (cal_date, day_type, holiday_name) VALUES ('2026-01-02', 'B', NULL);
INSERT INTO m_calendar (cal_date, day_type, holiday_name) VALUES ('2026-01-03', 'B', NULL);
INSERT INTO m_calendar (cal_date, day_type, holiday_name) VALUES ('2026-01-04', 'W', NULL);
