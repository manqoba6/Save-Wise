package com.savewise.service;

import com.savewise.dto.BudgetResponse;
import com.savewise.dto.DashboardResponse.HealthComponent;
import com.savewise.dto.DashboardResponse.HealthScore;
import com.savewise.dto.GoalResponse;
import com.savewise.entity.User;
import com.savewise.repository.SavingTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Financial Health Score (SDD ch. 11), 0-100, five factors worth 20 points each:
 * savings rate, expense-to-income ratio, budget adherence, goal progress, saving consistency.
 * The thresholds below are starting points; tune them as you see real data.
 */
@Service
public class FinancialHealthService {
    private static final int MAX = 20;
    private final SavingTransactionRepository savingTxs;

    public FinancialHealthService(SavingTransactionRepository savingTxs) { this.savingTxs = savingTxs; }

    public HealthScore calculate(User user, LocalDate month, BigDecimal income, BigDecimal expenses,
                                 List<BudgetResponse> budgets, List<GoalResponse> activeGoals) {
        boolean hasIncome = income.signum() > 0;
        double rate = hasIncome ? ratio(income.subtract(expenses), income) : 0;     // net / income
        double expenseRatio = hasIncome ? ratio(expenses, income) : 1;              // expenses / income

        List<HealthComponent> parts = new ArrayList<>();
        // 20% savings rate or better earns full marks.
        parts.add(part("Savings rate", hasIncome ? points(rate / 0.20) : 0));
        // Spending at or below 50% of income earns full marks; at or above 100% earns none.
        parts.add(part("Expense-to-income ratio", hasIncome ? points((1 - expenseRatio) / 0.5) : 0));
        parts.add(part("Budget adherence", budgetPoints(budgets)));
        parts.add(part("Goal progress", goalPoints(activeGoals)));
        parts.add(part("Saving consistency", consistencyPoints(user, month)));

        if (!hasIncome) return new HealthScore(0, "NOT_ENOUGH_DATA", parts);
        int total = parts.stream().mapToInt(HealthComponent::score).sum();
        String label = total >= 80 ? "EXCELLENT" : total >= 60 ? "GOOD" : total >= 40 ? "FAIR" : "NEEDS_ATTENTION";
        return new HealthScore(total, label, parts);
    }

    /** Share of budgets not yet exceeded; neutral (half marks) when the user has no budgets. */
    private int budgetPoints(List<BudgetResponse> budgets) {
        if (budgets.isEmpty()) return MAX / 2;
        long ok = budgets.stream().filter(b -> !"CRITICAL".equals(b.status())).count();
        return (int) Math.round((double) MAX * ok / budgets.size());
    }

    /** Average progress across active goals; neutral when there are none. */
    private int goalPoints(List<GoalResponse> goals) {
        if (goals.isEmpty()) return MAX / 2;
        double avg = goals.stream().mapToDouble(g -> g.percentComplete().doubleValue()).average().orElse(0);
        return points(avg / 100);
    }

    /** Months (out of the last 3, including this one) in which the user made at least one savings deposit. */
    private int consistencyPoints(User user, LocalDate month) {
        int months = 0;
        for (int i = 0; i < 3; i++) {
            LocalDate m = month.minusMonths(i);
            if (savingTxs.countDeposits(user.getId(), m, m.withDayOfMonth(m.lengthOfMonth())) > 0) months++;
        }
        return (int) Math.round((double) MAX * months / 3);
    }

    private static double ratio(BigDecimal a, BigDecimal b) {
        return a.divide(b, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private static int points(double fraction) {
        return (int) Math.round(Math.max(0, Math.min(1, fraction)) * MAX);
    }

    private static HealthComponent part(String name, int score) {
        return new HealthComponent(name, score, MAX);
    }
}
