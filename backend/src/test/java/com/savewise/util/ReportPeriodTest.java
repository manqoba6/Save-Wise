package com.savewise.util;

import com.savewise.util.ReportPeriod.Type;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportPeriodTest {
    @Test
    void weekRunsMondayToSunday() {
        ReportPeriod p = ReportPeriod.of(Type.WEEKLY, LocalDate.of(2026, 10, 15)); // a Thursday
        assertEquals(LocalDate.of(2026, 10, 12), p.from());
        assertEquals(LocalDate.of(2026, 10, 18), p.to());
    }

    @Test
    void sundayBelongsToTheWeekThatEndsOnIt() {
        ReportPeriod p = ReportPeriod.of(Type.WEEKLY, LocalDate.of(2026, 10, 18));
        assertEquals(LocalDate.of(2026, 10, 12), p.from());
    }

    @Test
    void monthCoversWholeMonthIncludingLeapFebruary() {
        ReportPeriod p = ReportPeriod.of(Type.MONTHLY, LocalDate.of(2028, 2, 10));
        assertEquals(LocalDate.of(2028, 2, 1), p.from());
        assertEquals(LocalDate.of(2028, 2, 29), p.to());
        assertEquals("February 2028", p.label());
    }

    @Test
    void annualAndDaily() {
        ReportPeriod a = ReportPeriod.of(Type.ANNUAL, LocalDate.of(2026, 6, 30));
        assertEquals(LocalDate.of(2026, 1, 1), a.from());
        assertEquals(LocalDate.of(2026, 12, 31), a.to());
        ReportPeriod d = ReportPeriod.of(Type.DAILY, LocalDate.of(2026, 10, 15));
        assertEquals(d.from(), d.to());
    }

    @Test
    void typeParsingIsForgiving() {
        assertEquals(Type.ANNUAL, ReportPeriod.parseType("yearly"));
        assertEquals(Type.MONTHLY, ReportPeriod.parseType(" monthly "));
        assertThrows(IllegalArgumentException.class, () -> ReportPeriod.parseType("hourly"));
    }
}
