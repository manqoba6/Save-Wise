package com.savewise.service;

import com.savewise.entity.Expense;
import com.savewise.entity.Income;
import com.savewise.entity.RecurringTransaction;
import com.savewise.repository.ExpenseRepository;
import com.savewise.repository.IncomeRepository;
import com.savewise.repository.RecurringTransactionRepository;
import com.savewise.util.RecurrenceCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/** Posts the income/expense records that a recurring schedule has become due for. */
@Service
public class RecurringExecutionService {
    /** Safety valve: a schedule that was dormant for years finishes catching up over several runs. */
    private static final int MAX_STEPS_PER_RUN = 366;

    private final RecurringTransactionRepository repo;
    private final IncomeRepository income;
    private final ExpenseRepository expenses;
    private final BudgetAlertService budgetAlerts;
    private final Clock clock;

    public RecurringExecutionService(RecurringTransactionRepository repo, IncomeRepository income,
                                     ExpenseRepository expenses, BudgetAlertService budgetAlerts, Clock clock) {
        this.repo = repo;
        this.income = income;
        this.expenses = expenses;
        this.budgetAlerts = budgetAlerts;
        this.clock = clock;
    }

    /**
     * Processes one schedule in its own transaction, so a failure here cannot block other users' schedules.
     * If the app was down for a few days, every missed date is posted (dated on the day it was due).
     *
     * @return how many records were created
     */
    @Transactional
    public int processOne(Long id) {
        LocalDate today = LocalDate.now(clock);
        Optional<RecurringTransaction> locked = repo.lockDue(id, today);
        if (locked.isEmpty()) return 0; // not due any more, or another instance is handling it

        RecurringTransaction r = locked.get();
        int created = 0;
        LocalDate due = r.getNextExecution();
        for (int step = 0; step < MAX_STEPS_PER_RUN && !due.isAfter(today); step++) {
            if (r.getEndDate() != null && due.isAfter(r.getEndDate())) break;
            if (post(r, due)) created++;
            due = RecurrenceCalculator.next(due, r.getFrequency(), r.getStartDate());
        }
        r.setNextExecution(due);
        if (r.getEndDate() != null && due.isAfter(r.getEndDate())) {
            r.setActive(false); // finished
        }
        return created;
    }

    /** Skips a date that already has a record from this schedule, so re-running is always safe. */
    private boolean post(RecurringTransaction r, LocalDate date) {
        String description = r.getDescription() == null || r.getDescription().isBlank()
                ? "Recurring transaction" : r.getDescription();
        if ("INCOME".equals(r.getTransactionType())) {
            if (income.existsByRecurringIdAndIncomeDate(r.getId(), date)) return false;
            income.save(Income.builder().user(r.getUser()).category(r.getCategory()).amount(r.getAmount())
                    .description(description).incomeDate(date).recurringId(r.getId()).build());
        } else {
            if (expenses.existsByRecurringIdAndExpenseDate(r.getId(), date)) return false;
            Expense saved = expenses.save(Expense.builder().user(r.getUser()).category(r.getCategory()).amount(r.getAmount())
                    .description(description).expenseDate(date).recurringId(r.getId()).build());
            budgetAlerts.check(r.getUser(), saved, saved.getAmount()); // recurring bills can trip budget alerts too
        }
        return true;
    }
}
