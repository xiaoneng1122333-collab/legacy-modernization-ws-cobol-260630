package com.practicebank.common.domain;

/** 日付種別。COBOL の CAL-REC-DAY-TYPE (B/H/W) に対応。 */
public enum DayType {
    B("Business", "営業日"),
    H("Holiday", "休日"),
    W("Weekend", "週末");

    private final String englishName;
    private final String japaneseName;

    DayType(String englishName, String japaneseName) {
        this.englishName = englishName;
        this.japaneseName = japaneseName;
    }

    public char toCode() {
        return name().charAt(0);
    }

    public static DayType fromCode(char code) {
        for (DayType t : values()) {
            if (t.toCode() == code) return t;
        }
        throw new IllegalArgumentException("Unknown day type code: " + code);
    }
}
