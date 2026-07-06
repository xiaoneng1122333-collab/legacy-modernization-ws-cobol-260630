package com.practicebank.masters.calendar;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/** CalendarMapper を呼び出すリポジトリ。 */
@Repository
public class CalendarRepository {

    private final CalendarMapper mapper;

    public CalendarRepository(CalendarMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<Calendar> findByDate(LocalDate calDate) {
        return mapper.findByDate(calDate);
    }

    public Optional<Calendar> findBusinessDay(LocalDate baseDate) {
        return mapper.findBusinessDay(baseDate);
    }
}
