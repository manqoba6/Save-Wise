package com.savewise.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecurrenceCalculatorTest {

    @Test
    void dailyAndWeekly() {
        LocalDate d = LocalDate.of(2026, 12, 30);
        assertEquals(LocalDate.of(2026, 12, 31), RecurrenceCalculator.next(d, "DAILY", d));
        assertEquals(LocalDate.of(2027, 1, 6), RecurrenceCalculator.next(d, "WEEKLY", d));
    }

    @Test
    void monthlyKeepsAnchorDayAcrossShortMonths() {
        LocalDate anchor = LocalDate.of(2026, 1, 31);
        LocalDate feb = RecurrenceCalculator.next(anchor, "MONTHLY", anchor);
        LocalDate mar = RecurrenceCalculator.next(feb, "MONTHLY", anchor);
        LocalDate apr = RecurrenceCalculator.next(mar, "MONTHLY", anchor);
        assertEquals(LocalDate.of(2026, 2, 28), feb);
        assertEquals(LocalDate.of(2026, 3, 31), mar);
        assertEquals(LocalDate.of(2026, 4, 30), apr);
    }

    @Test
    void monthlyRollsOverTheYear() {
        LocalDate anchor = LocalDate.of(2026, 12, 15);
        assertEquals(LocalDate.of(2027, 1, 15), RecurrenceCalculator.next(anchor, "MONTHLY", anchor));
    }

    @Test
    void yearlyHandlesLeapDay() {
        LocalDate anchor = LocalDate.of(2028, 2, 29);
        LocalDate y1 = RecurrenceCalculator.next(anchor, "YEARLY", anchor);
        LocalDate y4 = RecurrenceCalculator.next(LocalDate.of(2031, 2, 28), "YEARLY", anchor);
        assertEquals(LocalDate.of(2029, 2, 28), y1);
        assertEquals(LocalDate.of(2032, 2, 29), y4);
    }

    @Test
    void unknownFrequencyIsRejected() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> RecurrenceCalculator.next(d, "FORTNIGHTLY", d));
    }
}
