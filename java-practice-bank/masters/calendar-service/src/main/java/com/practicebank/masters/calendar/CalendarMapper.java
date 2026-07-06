package com.practicebank.masters.calendar;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Optional;

/** 営業日マスタテーブル (m_calendar) への MyBatis マッパー。 */
@Mapper
public interface CalendarMapper {

    @Select("""
            SELECT cal_date, day_type, holiday_name
              FROM m_calendar
             WHERE cal_date = #{calDate}
            """)
    Optional<Calendar> findByDate(@Param("calDate") LocalDate calDate);

    @Select("""
            SELECT cal_date, day_type, holiday_name
              FROM m_calendar
             WHERE cal_date = (
                   SELECT MIN(cal_date)
                     FROM m_calendar
                    WHERE cal_date >= #{baseDate}
                      AND day_type = 'B'
                   )
            """)
    Optional<Calendar> findBusinessDay(@Param("baseDate") LocalDate baseDate);
}
