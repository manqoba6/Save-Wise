package com.savewise.service;

import com.savewise.dto.AssistantDto.GoalForecast;
import com.savewise.dto.CategorySpend;
import com.savewise.dto.GoalResponse;
import com.savewise.entity.User;
import com.savewise.repository.ExpenseRepository;
import com.savewise.repository.IncomeRepository;
import com.savewise.repository.SavingTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Month-by-month figures shared by the assistant and the prediction engine. */
@Service
public class AnalyticsService {

    /** savingsNet = deposits - withdrawals into savings accounts during the month. */
    public record MonthStat(YearMonth month, BigDecimal income, BigDecimal expenses, BigDecimal savingsNet) {
        public BigDecimal net() { return income.subtract(expenses); }
        boolean hasActivity() { return income.signum() != 0 || expenses.signum() != 0 || savingsNet.signum() != 0; }
    }

    private final IncomeRepository income;
    private final ExpenseRepository expenses;
    private final SavingTransactionRepository savingTxs;
    private final GoalService goalService;
    private final Clock clock;

    public AnalyticsService(IncomeRepository income, ExpenseRepository expenses, SavingTransactionRepository savingTxs,
                            GoalService goalService, Clock clock) {
        this.income = income;
        this.expenses = expenses;
        this.savingTxs = savingTxs;
        this.goalService = goalService;
        this.clock = clock;
    }

    public LocalDate today() { return LocalDate.now(clock); }

    /**
     * The last {@code maxMonths} COMPLETED months, oldest first. The current month is left out because a
     * half-finished month would drag every average down. Months before the user's first activity are dropped,
     * so a new user's empty history does not dilute the averages (quiet months after that still count as zero).
     */
    @Transactional(readOnly = true)
    public List<MonthStat> completedMonths(User user, int maxMonths) {
        YearMonth current = YearMonth.from(today());
        List<MonthStat> all = new ArrayList<>();
        for (int i = maxMonths; i >= 1; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate from = ym.atDay(1), to = ym.atEndOfMonth();
            BigDecimal deposits = savingTxs.sumByType(user.getId(), "DEPOSIT", from, to);
            BigDecimal withdrawals = savingTxs.sumByType(user.getId(), "WITHDRAW", from, to);
            all.add(new MonthStat(ym, income.sumBetween(user.getId(), from, to),
                    expenses.sumBetween(user.getId(), from, to), deposits.subtract(withdrawals)));
        }
        int firstActive = 0;
        while (firstActive < all.size() && !all.get(firstActive).hasActivity()) firstActive++;
        return all.subList(firstActive, all.size());
    }

    /** Expense totals per category for each month of the window (same order as the window). */
    @Transactional(readOnly = true)
    public List<Map<String, BigDecimal>> categorySpendByMonth(User user, List<MonthStat> window) {
        List<Map<String, BigDecimal>> out = new ArrayList<>();
        for (MonthStat m : window) {
            Map<String, BigDecimal> map = new LinkedHashMap<>();
            for (CategorySpend c : expenses.spendByCategory(user.getId(), m.month().atDay(1), m.month().atEndOfMonth())) {
                map.put(c.categoryName(), c.amount());
            }
            out.add(map);
        }
        return out;
    }

    /**
     * For each active goal: how much is left, what it needs per month to hit its deadline, and whether the
     * user's average monthly surplus covers that. Each goal is judged on its own against the whole surplus.
     */
    public List<GoalForecast> goalForecasts(User user, BigDecimal averageSurplus) {
        YearMonth now = YearMonth.from(today());
        List<GoalForecast> out = new ArrayList<>();
        for (GoalResponse g : goalService.list(user, "ACTIVE")) {
            BigDecimal remaining = g.targetAmount().subtract(g.currentAmount()).max(BigDecimal.ZERO);
            Integer monthsAtPace = averageSurplus.signum() > 0
                    ? remaining.divide(averageSurplus, 0, RoundingMode.CEILING).intValue() : null;
            BigDecimal required = null;
            boolean onTrack;
            if (g.deadline() != null) {
                long monthsLeft = Math.max(1, ChronoUnit.MONTHS.between(now, YearMonth.from(g.deadline())));
                required = remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.CEILING);
                onTrack = remaining.signum() == 0 || averageSurplus.compareTo(required) >= 0;
            } else {
                onTrack = remaining.signum() == 0 || averageSurplus.signum() > 0;
            }
            out.add(new GoalForecast(g.id(), g.title(), remaining, g.deadline(), required, monthsAtPace, onTrack));
        }
        return out;
    }
}
