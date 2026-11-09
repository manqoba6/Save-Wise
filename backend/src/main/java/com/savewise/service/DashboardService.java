package com.savewise.service;

import com.savewise.dto.*;
import com.savewise.dto.DashboardResponse.*;
import com.savewise.entity.User;
import com.savewise.repository.ExpenseRepository;
import com.savewise.repository.IncomeRepository;
import com.savewise.repository.SavingAccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DashboardService {
    private static final int RECENT_LIMIT = 10;
    private static final int CHART_MONTHS = 6;

    private final IncomeRepository income;
    private final ExpenseRepository expenses;
    private final SavingAccountRepository savingAccounts;
    private final BudgetService budgetService;
    private final GoalService goalService;
    private final NotificationService notifications;
    private final FinancialHealthService health;

    public DashboardService(IncomeRepository income, ExpenseRepository expenses, SavingAccountRepository savingAccounts,
                            BudgetService budgetService, GoalService goalService,
                            NotificationService notifications, FinancialHealthService health) {
        this.income = income;
        this.expenses = expenses;
        this.savingAccounts = savingAccounts;
        this.budgetService = budgetService;
        this.goalService = goalService;
        this.notifications = notifications;
        this.health = health;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get(User user, LocalDate monthParam) {
        Long uid = user.getId();
        LocalDate month = (monthParam == null ? LocalDate.now() : monthParam).withDayOfMonth(1);
        LocalDate end = month.withDayOfMonth(month.lengthOfMonth());

        BigDecimal monthIncome = income.sumBetween(uid, month, end);
        BigDecimal monthExpenses = expenses.sumBetween(uid, month, end);
        BigDecimal totalIncome = income.sumAll(uid);
        BigDecimal totalExpenses = expenses.sumAll(uid);

        BigDecimal rate = monthIncome.signum() > 0
                ? monthIncome.subtract(monthExpenses).multiply(BigDecimal.valueOf(100)).divide(monthIncome, 1, RoundingMode.HALF_UP)
                : null;
        Summary summary = new Summary(monthIncome, monthExpenses, monthIncome.subtract(monthExpenses), rate,
                totalIncome, totalExpenses, totalIncome.subtract(totalExpenses), savingAccounts.sumBalances(uid));

        List<BudgetResponse> budgets = budgetService.list(user, month);
        List<GoalResponse> activeGoals = goalService.list(user, "ACTIVE");

        return new DashboardResponse(
                month,
                summary,
                budgets,
                activeGoals,
                expenses.spendByCategory(uid, month, end),
                monthlyChart(uid, month),
                recent(uid),
                health.calculate(user, month, monthIncome, monthExpenses, budgets, activeGoals),
                notifications.unreadCount(user));
    }

    /** Income vs expenses for the selected month and the five before it, oldest first. */
    private List<MonthlyPoint> monthlyChart(Long uid, LocalDate month) {
        List<MonthlyPoint> points = new ArrayList<>();
        for (int i = CHART_MONTHS - 1; i >= 0; i--) {
            LocalDate m = month.minusMonths(i);
            LocalDate last = m.withDayOfMonth(m.lengthOfMonth());
            BigDecimal in = income.sumBetween(uid, m, last);
            BigDecimal out = expenses.sumBetween(uid, m, last);
            points.add(new MonthlyPoint(YearMonth.from(m).toString(), in, out, in.subtract(out)));
        }
        return points;
    }

    /** Latest income and expense records merged into one list, newest first. */
    private List<RecentTransaction> recent(Long uid) {
        var page = PageRequest.of(0, RECENT_LIMIT);
        List<RecentTransaction> all = new ArrayList<>();
        income.findByUserIdOrderByIncomeDateDescIdDesc(uid, page).forEach(i -> all.add(new RecentTransaction(
                i.getId(), "INCOME", i.getCategory().getName(), i.getAmount(), i.getDescription(), i.getIncomeDate())));
        expenses.findByUserIdOrderByExpenseDateDescIdDesc(uid, page).forEach(e -> all.add(new RecentTransaction(
                e.getId(), "EXPENSE", e.getCategory().getName(), e.getAmount(), e.getDescription(), e.getExpenseDate())));
        all.sort(Comparator.comparing(RecentTransaction::date).reversed());
        return all.size() > RECENT_LIMIT ? all.subList(0, RECENT_LIMIT) : all;
    }
}
