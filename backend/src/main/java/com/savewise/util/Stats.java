package com.savewise.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Small statistics helpers for the assistant and predictions. All pure functions. */
public final class Stats {
    private Stats() {}

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO.setScale(2);
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /** Sample standard deviation (n - 1). Zero when there are fewer than two values. */
    public static BigDecimal stdDev(List<BigDecimal> values) {
        int n = values.size();
        if (n < 2) return BigDecimal.ZERO.setScale(2);
        double mean = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double sumSq = values.stream().mapToDouble(v -> Math.pow(v.doubleValue() - mean, 2)).sum();
        return BigDecimal.valueOf(Math.sqrt(sumSq / (n - 1))).setScale(2, RoundingMode.HALF_UP);
    }

    /** Least-squares slope of the values against 0, 1, 2 ...: how much the value changes per step. */
    public static double slope(List<BigDecimal> values) {
        int n = values.size();
        if (n < 2) return 0;
        double xMean = (n - 1) / 2.0;
        double yMean = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            num += (i - xMean) * (values.get(i).doubleValue() - yMean);
            den += (i - xMean) * (i - xMean);
        }
        return num / den;
    }

    /** The slope as a percentage of the mean: "+4.2" means rising about 4.2% of the average each step. */
    public static BigDecimal slopePercentOfMean(List<BigDecimal> values) {
        double mean = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        if (mean <= 0) return BigDecimal.ZERO.setScale(1);
        return BigDecimal.valueOf(slope(values) / mean * 100).setScale(1, RoundingMode.HALF_UP);
    }

    /** RISING / FALLING when the monthly change is 3% or more either way. Needs at least 3 data points. */
    public static String direction(BigDecimal slopePercent, int dataPoints) {
        if (dataPoints < 3) return "INSUFFICIENT_DATA";
        if (slopePercent.compareTo(BigDecimal.valueOf(3)) >= 0) return "RISING";
        if (slopePercent.compareTo(BigDecimal.valueOf(-3)) <= 0) return "FALLING";
        return "STABLE";
    }

    /** (current - base) / base as a percentage to 1 decimal place, or null when there is no base to compare to. */
    public static BigDecimal changePercent(BigDecimal current, BigDecimal base) {
        if (base.signum() <= 0) return null;
        return current.subtract(base).multiply(HUNDRED).divide(base, 1, RoundingMode.HALF_UP);
    }

    /** Rounds to a friendly figure for suggestions ("save R500 more", not "R487.31"). Never returns less than one step. */
    public static BigDecimal niceRound(BigDecimal value) {
        BigDecimal v = value.abs();
        int step = v.compareTo(BigDecimal.valueOf(100)) < 0 ? 10
                : v.compareTo(BigDecimal.valueOf(1000)) < 0 ? 50
                : v.compareTo(BigDecimal.valueOf(10000)) < 0 ? 100 : 500;
        BigDecimal s = BigDecimal.valueOf(step);
        BigDecimal rounded = v.divide(s, 0, RoundingMode.HALF_UP).multiply(s);
        return (rounded.signum() == 0 ? s : rounded).setScale(2);
    }
}
