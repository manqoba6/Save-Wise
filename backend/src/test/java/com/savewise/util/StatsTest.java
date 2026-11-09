package com.savewise.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatsTest {
    private static List<BigDecimal> list(double... v) {
        return java.util.Arrays.stream(v).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void meanAndStdDev() {
        assertEquals(new BigDecimal("200.00"), Stats.mean(list(100, 200, 300)));
        assertEquals(new BigDecimal("100.00"), Stats.stdDev(list(100, 200, 300)));
        assertEquals(new BigDecimal("0.00"), Stats.stdDev(list(500)));
        assertEquals(new BigDecimal("0.00"), Stats.mean(List.of()));
    }

    @Test
    void slopeOfALine() {
        assertEquals(50.0, Stats.slope(list(100, 150, 200, 250)), 0.0001);
        assertEquals(0.0, Stats.slope(list(300, 300, 300)), 0.0001);
    }

    @Test
    void directionNeedsThreePointsAndThreePercent() {
        assertEquals("RISING", Stats.direction(Stats.slopePercentOfMean(list(100, 120, 140, 160)), 4));
        assertEquals("FALLING", Stats.direction(Stats.slopePercentOfMean(list(160, 140, 120, 100)), 4));
        assertEquals("STABLE", Stats.direction(Stats.slopePercentOfMean(list(100, 101, 100, 101)), 4));
        assertEquals("INSUFFICIENT_DATA", Stats.direction(new BigDecimal("50"), 2));
    }

    @Test
    void changePercent() {
        assertEquals(new BigDecimal("25.0"), Stats.changePercent(new BigDecimal("125"), new BigDecimal("100")));
        assertNull(Stats.changePercent(new BigDecimal("125"), BigDecimal.ZERO));
    }

    @Test
    void niceRoundUsesFriendlySteps() {
        assertEquals(new BigDecimal("500.00"), Stats.niceRound(new BigDecimal("487.31"))); // step 50: 9.75 steps rounds to 10
        assertEquals(new BigDecimal("10.00"), Stats.niceRound(new BigDecimal("2")));
        assertEquals(new BigDecimal("500.00"), Stats.niceRound(new BigDecimal("480")));
        assertEquals(new BigDecimal("1200.00"), Stats.niceRound(new BigDecimal("1187")));
        assertEquals(new BigDecimal("15500.00"), Stats.niceRound(new BigDecimal("15387")));
    }
}
