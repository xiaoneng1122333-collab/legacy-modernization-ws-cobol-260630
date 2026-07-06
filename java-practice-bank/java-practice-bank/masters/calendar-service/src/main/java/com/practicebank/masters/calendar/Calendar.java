package com.practicebank.masters.calendar;

import com.practicebank.common.domain.DayType;

import java.time.LocalDate;

/** 営業日マスタの 1 行。COBOL の CAL-REC に対応する。 */
public record Calendar(
        LocalDate calDate,
        DayType dayType,
        String holidayName
) {
}
