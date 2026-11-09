package com.savewise.dto;

import com.savewise.dto.DashboardResponse.HealthScore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Response shapes for the smart assistant (/api/assistant/**) and predictions (/api/predictions). */
public final class AssistantDto {
    private AssistantDto() {}

    /**
     * One piece of advice or one finding. severity is CRITICAL, WARNING or INFO.
     * type examples: BUDGET_EXCEEDED, BUDGET_NEAR_LIMIT, BUDGET_PACE, SPENDING_SPIKE, EXPENSES_EXCEED_INCOME,
     * SAVE_MORE, REDUCE_CATEGORY, EMERGENCY_FUND, GOAL_OFF_TRACK, SET_BUDGET, TREND_SPENDING, TREND_CATEGORY, ALL_CLEAR.
     * amount is the figure the message is about (an overspend, or a suggested monthly amount) when there is one.
     */
    public record Insight(String type, String severity, String title, String message, String categoryName, BigDecimal amount) {
    }

    public record AdviceResponse(LocalDate month, String currency, HealthScore financialHealth, List<Insight> insights) {
    }

    public record OverspendingResponse(LocalDate month, String currency, BigDecimal income, BigDecimal expenses,
                                       List<Insight> findings) {
    }

    public record RecommendationsResponse(String currency, int monthsAnalysed, BigDecimal averageMonthlyIncome,
                                          BigDecimal averageMonthlyExpenses, BigDecimal averageMonthlySurplus,
                                          BigDecimal averageSavingsRatePercent, List<Insight> recommendations) {
    }

    public record MonthPoint(String month, BigDecimal income, BigDecimal expenses, BigDecimal net,
                             BigDecimal savingsRatePercent) {
    }

    /** direction is RISING, FALLING, STABLE or NEW (nothing to compare against). */
    public record CategoryTrend(String categoryName, BigDecimal lastMonth, BigDecimal previousAverage,
                                BigDecimal changePercent, String direction) {
    }

    /**
     * Trends cover completed months only. incomeTrend/expenseTrend are RISING, FALLING, STABLE or INSUFFICIENT_DATA
     * (needs 3 months); the ...ChangePerMonthPercent figures say how fast.
     */
    public record TrendsResponse(String currency, int monthsAnalysed, List<MonthPoint> months,
                                 String incomeTrend, BigDecimal incomeChangePerMonthPercent,
                                 String expenseTrend, BigDecimal expenseChangePerMonthPercent,
                                 List<CategoryTrend> categories, String message) {
    }

    /** requiredMonthly and monthsToGoalAtCurrentPace are null when they cannot be worked out (no deadline / no surplus). */
    public record GoalForecast(Long goalId, String title, BigDecimal remaining, LocalDate deadline,
                               BigDecimal requiredMonthly, Integer monthsToGoalAtCurrentPace, boolean onTrack) {
    }

    /** balanceLow/High are a one-standard-deviation range around projectedBalance. */
    public record Projection(int monthsAhead, String month, BigDecimal projectedBalance, BigDecimal balanceLow,
                             BigDecimal balanceHigh, BigDecimal projectedSavings) {
    }

    /** confidence is LOW (under 3 months of history), MEDIUM (3 to 5) or HIGH (6 or more). */
    public record PredictionResponse(String currency, boolean enoughData, String confidence, int monthsAnalysed,
                                     String message, BigDecimal currentBalance, BigDecimal currentSavings,
                                     BigDecimal averageMonthlyIncome, BigDecimal averageMonthlyExpenses,
                                     BigDecimal averageMonthlyNet, BigDecimal averageMonthlySavingsContribution,
                                     String incomeTrend, String expenseTrend,
                                     List<Projection> projections, List<GoalForecast> goals) {
    }
}
