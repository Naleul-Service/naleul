package com.naleul.naleul.global.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class KstDateTimeUtils {

    private KstDateTimeUtils() {}

    public static LocalDateTime dayStart(LocalDate date) {
        return date.atStartOfDay();
    }

    public static LocalDateTime dayEnd(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }

    public static LocalDate toDate(LocalDateTime dateTime) {
        return dateTime.toLocalDate();
    }
}