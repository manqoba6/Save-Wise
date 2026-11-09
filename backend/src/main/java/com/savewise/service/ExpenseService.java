package com.savewise.service;

import com.savewise.dto.ExpenseRequest;
import com.savewise.dto.ExpenseResponse;
import com.savewise.dto.PageResponse;
import com.savewise.entity.Expense;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.ExpenseRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * After every create or update, BudgetAlertService raises the 80% / 100% budget notifications
 * in the same transaction as the expense.
 */
@Service
public class ExpenseService {
    private final ExpenseRepository repo;
    private final CategoryService categories;
    private final BudgetAlertService budgetAlerts;

    public ExpenseService(ExpenseRepository repo, CategoryService categories, BudgetAlertService budgetAlerts) {
        this.repo = repo;
        this.categories = categories;
        this.budgetAlerts = budgetAlerts;
    }

    @Transactional
    public ExpenseResponse create(User user, ExpenseRequest req) {
        Expense e = Expense.builder()
                .user(user)
                .category(categories.requireOfType(req.categoryId(), "EXPENSE"))
                .amount(req.amount())
                .paymentMethod(req.paymentMethod())
                .description(req.description())
                .expenseDate(req.expenseDate())
                .build();
        Expense saved = repo.save(e);
        budgetAlerts.check(user, saved, saved.getAmount());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(User user, Long id) {
        return toResponse(owned(user, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> search(User user, Long categoryId, LocalDate from, LocalDate to,
                                                String q, int page, int size) {
        Specification<Expense> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("user").get("id"), user.getId()));
            if (categoryId != null) p.add(cb.equal(root.get("category").get("id"), categoryId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            if (q != null && !q.isBlank()) {
                p.add(cb.like(cb.lower(root.get("description")), "%" + q.trim().toLowerCase() + "%"));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "expenseDate", "id"));
        return PageResponse.from(repo.findAll(spec, pageable).map(ExpenseService::toResponse));
    }

    @Transactional
    public ExpenseResponse update(User user, Long id, ExpenseRequest req) {
        Expense e = owned(user, id);
        Long oldCategoryId = e.getCategory().getId();
        LocalDate oldMonth = e.getExpenseDate().withDayOfMonth(1);
        BigDecimal oldAmount = e.getAmount();

        e.setCategory(categories.requireOfType(req.categoryId(), "EXPENSE"));
        e.setAmount(req.amount());
        e.setPaymentMethod(req.paymentMethod());
        e.setDescription(req.description());
        e.setExpenseDate(req.expenseDate());

        // How much did this edit add to the (new) category-month total?
        boolean sameBucket = oldCategoryId.equals(e.getCategory().getId())
                && oldMonth.equals(e.getExpenseDate().withDayOfMonth(1));
        budgetAlerts.check(user, e, sameBucket ? e.getAmount().subtract(oldAmount) : e.getAmount());
        return toResponse(e);
    }

    @Transactional
    public void delete(User user, Long id) {
        repo.delete(owned(user, id));
    }

    private Expense owned(User user, Long id) {
        return repo.findById(id)
                .filter(e -> e.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Expense not found."));
    }

    static ExpenseResponse toResponse(Expense e) {
        return new ExpenseResponse(e.getId(), e.getCategory().getId(), e.getCategory().getName(),
                e.getAmount(), e.getPaymentMethod(), e.getDescription(), e.getExpenseDate());
    }
}
