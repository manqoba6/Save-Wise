package com.savewise.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** The date range a report covers, worked out from a report type and any date inside the period. */
public record ReportPeriod(Type type, LocalDate from, LocalDate to, String label) {

    public enum Type { DAILY, WEEKLY, MONTHLY, ANNUAL }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    /** Accepts DAILY, WEEKLY, MONTHLY, ANNUAL (YEARLY is accepted as an alias), in any case. */
    public static Type parseType(String raw) {
        String t = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (t.equals("YEARLY")) t = "ANNUAL";
        try {
            return Type.valueOf(t);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Report type must be DAILY, WEEKLY, MONTHLY or ANNUAL.");
        }
    }

    /** Weeks run Monday to Sunday. */
    public static ReportPeriod of(Type type, LocalDate date) {
        return switch (type) {
            case DAILY -> new ReportPeriod(type, date, date, date.format(DAY));
            case WEEKLY -> {
                LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate sunday = monday.plusDays(6);
                yield new ReportPeriod(type, monday, sunday, monday.format(DAY) + " to " + sunday.format(DAY));
            }
            case MONTHLY -> {
                LocalDate first = date.withDayOfMonth(1);
                yield new ReportPeriod(type, first, first.withDayOfMonth(first.lengthOfMonth()), first.format(MONTH));
            }
            case ANNUAL -> new ReportPeriod(type, LocalDate.of(date.getYear(), 1, 1),
                    LocalDate.of(date.getYear(), 12, 31), String.valueOf(date.getYear()));
        };
    }
}
