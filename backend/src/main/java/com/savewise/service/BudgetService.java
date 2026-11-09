package com.savewise.service;

import com.savewise.dto.BudgetRequest;
import com.savewise.dto.BudgetResponse;
import com.savewise.entity.Budget;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.BudgetRepository;
import com.savewise.repository.ExpenseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class BudgetService {
    private final BudgetRepository repo;
    private final ExpenseRepository expenses;
    private final CategoryService categories;

    public BudgetService(BudgetRepository repo, ExpenseRepository expenses, CategoryService categories) {
        this.repo = repo;
        this.expenses = expenses;
        this.categories = categories;
    }

    @Transactional
    public BudgetResponse create(User user, BudgetRequest req) {
        LocalDate month = req.budgetMonth().withDayOfMonth(1);
        var category = categories.requireOfType(req.categoryId(), "EXPENSE");
        if (repo.findByUserIdAndCategoryIdAndBudgetMonth(user.getId(), category.getId(), month).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have a budget for this category in that month.");
        }
        Budget b = repo.save(Budget.builder().user(user).category(category)
                .budgetMonth(month).budgetAmount(req.budgetAmount()).build());
        return toResponse(b);
    }

    /** Budgets for one month (defaults to the current month), each with spend progress. */
    @Transactional(readOnly = true)
    public List<BudgetResponse> list(User user, LocalDate month) {
        LocalDate m = (month == null ? LocalDate.now() : month).withDayOfMonth(1);
        return repo.findByUserIdAndBudgetMonthOrderByIdAsc(user.getId(), m).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(User user, Long id) {
        return toResponse(owned(user, id));
    }

    @Transactional
    public BudgetResponse update(User user, Long id, BudgetRequest req) {
        Budget b = owned(user, id);
        LocalDate month = req.budgetMonth().withDayOfMonth(1);
        var category = categories.requireOfType(req.categoryId(), "EXPENSE");
        repo.findByUserIdAndCategoryIdAndBudgetMonth(user.getId(), category.getId(), month)
                .filter(other -> !other.getId().equals(b.getId()))
                .ifPresent(other -> {
                    throw new ApiException(HttpStatus.CONFLICT, "You already have a budget for this category in that month.");
                });
        b.setCategory(category);
        b.setBudgetMonth(month);
        b.setBudgetAmount(req.budgetAmount());
        return toResponse(b);
    }

    @Transactional
    public void delete(User user, Long id) {
        repo.delete(owned(user, id));
    }

    private Budget owned(User user, Long id) {
        return repo.findById(id)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Budget not found."));
    }

    private BudgetResponse toResponse(Budget b) {
        LocalDate from = b.getBudgetMonth();
        BigDecimal spent = expenses.sumSpent(b.getUser().getId(), b.getCategory().getId(), from, from.withDayOfMonth(from.lengthOfMonth()));
        BigDecimal percent = spent.multiply(BigDecimal.valueOf(100)).divide(b.getBudgetAmount(), 1, RoundingMode.HALF_UP);
        String status = percent.compareTo(BigDecimal.valueOf(100)) >= 0 ? "CRITICAL"
                : percent.compareTo(BigDecimal.valueOf(80)) >= 0 ? "WARNING" : "OK";
        return new BudgetResponse(b.getId(), b.getCategory().getId(), b.getCategory().getName(), b.getBudgetMonth(),
                b.getBudgetAmount(), spent, b.getBudgetAmount().subtract(spent), percent, status);
    }
}
