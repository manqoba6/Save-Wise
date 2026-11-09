package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate month,
        Summary summary,
        List<BudgetResponse> budgets,
        List<GoalResponse> activeGoals,
        List<CategorySpend> spendingByCategory,
        List<MonthlyPoint> monthlyChart,
        List<RecentTransaction> recentTransactions,
        HealthScore financialHealth,
        long unreadNotifications) {

    /**
     * monthIncome/monthExpenses/monthNet are for the selected month; the totals are all-time.
     * currentBalance = totalIncome - totalExpenses (savings accounts are reported separately in totalSavings).
     * savingsRatePercent is null when the month has no income.
     */
    public record Summary(BigDecimal monthIncome, BigDecimal monthExpenses, BigDecimal monthNet,
                          BigDecimal savingsRatePercent, BigDecimal totalIncome, BigDecimal totalExpenses,
                          BigDecimal currentBalance, BigDecimal totalSavings) {
    }

    /** One bar group in the 6-month chart. month is "yyyy-MM". */
    public record MonthlyPoint(String month, BigDecimal income, BigDecimal expenses, BigDecimal net) {
    }

    /** type is INCOME or EXPENSE. */
    public record RecentTransaction(Long id, String type, String categoryName, BigDecimal amount,
                                    String description, LocalDate date) {
    }

    public record HealthComponent(String name, int score, int maxScore) {
    }

    /** score is 0-100. label: EXCELLENT, GOOD, FAIR, NEEDS_ATTENTION or NOT_ENOUGH_DATA. */
    public record HealthScore(int score, String label, List<HealthComponent> components) {
    }
}
