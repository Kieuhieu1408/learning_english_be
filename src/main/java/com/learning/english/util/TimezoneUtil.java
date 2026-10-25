package com.learning.english.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class TimezoneUtil {

    public static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    private TimezoneUtil() {
    }

    public static ZoneId parseZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    public static LocalDate today(ZoneId zoneId) {
        return LocalDate.now(zoneId != null ? zoneId : ZoneId.of(DEFAULT_TIMEZONE));
    }

    public static Instant startOfDayInstant(LocalDate date, ZoneId zoneId) {
        ZoneId z = zoneId != null ? zoneId : ZoneId.of(DEFAULT_TIMEZONE);
        return date.atStartOfDay(z).toInstant();
    }

    public static Instant endOfDayInstant(LocalDate date, ZoneId zoneId) {
        ZoneId z = zoneId != null ? zoneId : ZoneId.of(DEFAULT_TIMEZONE);
        return date.plusDays(1).atStartOfDay(z).toInstant().minusMillis(1);
    }

    public static String formatDate(Instant instant, ZoneId zoneId) {
        ZoneId z = zoneId != null ? zoneId : ZoneId.of(DEFAULT_TIMEZONE);
        if (instant == null) {
            return LocalDate.now(z).toString();
        }
        return instant.atZone(z).toLocalDate().toString();
    }

    public static Instant parseDateOrInstant(String value, ZoneId zoneId) {
        ZoneId z = zoneId != null ? zoneId : ZoneId.of(DEFAULT_TIMEZONE);
        if (value == null || value.isBlank()) {
            return LocalDate.now(z).atStartOfDay(z).toInstant();
        }
        String trimmed = value.trim();
        try {
            if (trimmed.contains("T")) {
                return Instant.parse(trimmed);
            }
            return LocalDate.parse(trimmed).atStartOfDay(z).toInstant();
        } catch (Exception e) {
            return LocalDate.now(z).atStartOfDay(z).toInstant();
        }
    }
}
