package com.savewise.util;

import java.time.LocalDate;
import java.time.YearMonth;

public final class RecurrenceCalculator {
    private RecurrenceCalculator() {}

    /**
     * The occurrence after {@code current}.
     *
     * @param anchor the schedule's start date. Monthly and yearly schedules snap back to the anchor's
     *               day, so a monthly schedule starting on 31 Jan runs 28 Feb, 31 Mar, 30 Apr, 31 May
     *               rather than drifting to the 28th forever.
     */
    public static LocalDate next(LocalDate current, String frequency, LocalDate anchor) {
        return switch (frequency) {
            case "DAILY" -> current.plusDays(1);
            case "WEEKLY" -> current.plusWeeks(1);
            case "MONTHLY" -> {
                YearMonth ym = YearMonth.from(current).plusMonths(1);
                yield ym.atDay(Math.min(anchor.getDayOfMonth(), ym.lengthOfMonth()));
            }
            case "YEARLY" -> {
                YearMonth ym = YearMonth.of(current.getYear() + 1, anchor.getMonth());
                yield ym.atDay(Math.min(anchor.getDayOfMonth(), ym.lengthOfMonth()));
            }
            default -> throw new IllegalArgumentException("Unknown frequency: " + frequency);
        };
    }
}
