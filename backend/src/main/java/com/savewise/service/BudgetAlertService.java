package com.savewise.service;

import com.savewise.entity.Expense;
import com.savewise.entity.User;
import com.savewise.repository.BudgetRepository;
import com.savewise.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import com.savewise.util.CurrencyFormatter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Business rule (SRS ch. 5): alert at 80% (warning) and 100% (critical) of a category's monthly budget.
 * An alert fires only when an expense moves spending ACROSS a threshold, so users are not spammed
 * with a new notification for every expense after they are already over budget.
 */
@Service
public class BudgetAlertService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final BudgetRepository budgets;
    private final ExpenseRepository expenses;
    private final NotificationService notifications;
    private final PreferenceService preferences;

    public BudgetAlertService(BudgetRepository budgets, ExpenseRepository expenses,
                              NotificationService notifications, PreferenceService preferences) {
        this.budgets = budgets;
        this.expenses = expenses;
        this.notifications = notifications;
        this.preferences = preferences;
    }

    /**
     * @param added how much this save added to the category's monthly total
     *              (the full amount for a new expense, the increase for an edit).
     */
    public void check(User user, Expense expense, BigDecimal added) {
        if (added == null || added.signum() <= 0) return;
        LocalDate month = expense.getExpenseDate().withDayOfMonth(1);
        Long categoryId = expense.getCategory().getId();

        budgets.findByUserIdAndCategoryIdAndBudgetMonth(user.getId(), categoryId, month).ifPresent(budget -> {
            BigDecimal spent = expenses.sumSpent(user.getId(), categoryId, month, month.withDayOfMonth(month.lengthOfMonth()));
            BigDecimal previous = spent.subtract(added);
            BigDecimal limit = budget.getBudgetAmount();
            String label = expense.getCategory().getName() + " budget for "
                    + month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
            String cur = preferences.currencyFor(user);
            String figures = "(" + CurrencyFormatter.format(spent, cur) + " of " + CurrencyFormatter.format(limit, cur) + ")";

            if (crossed(previous, spent, limit, 100)) {
                notifications.create(user, "BUDGET_CRITICAL", "Budget exceeded",
                        "You have used your entire " + label + " " + figures + ".");
            } else if (crossed(previous, spent, limit, 80)) {
                notifications.create(user, "BUDGET_WARNING", "Budget warning",
                        "You have used 80% or more of your " + label + " " + figures + ".");
            }
        });
    }

    private static boolean crossed(BigDecimal previous, BigDecimal spent, BigDecimal limit, int percent) {
        BigDecimal threshold = limit.multiply(BigDecimal.valueOf(percent));
        return previous.multiply(HUNDRED).compareTo(threshold) < 0 && spent.multiply(HUNDRED).compareTo(threshold) >= 0;
    }
}
