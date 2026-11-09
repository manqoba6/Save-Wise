package com.savewise.service;

import com.savewise.dto.*;
import com.savewise.entity.RecurringTransaction;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.ExpenseRepository;
import com.savewise.repository.IncomeRepository;
import com.savewise.repository.RecurringTransactionRepository;
import com.savewise.util.RecurrenceCalculator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class RecurringTransactionService {
    private final RecurringTransactionRepository repo;
    private final CategoryService categories;
    private final IncomeRepository income;
    private final ExpenseRepository expenses;
    private final Clock clock;

    public RecurringTransactionService(RecurringTransactionRepository repo, CategoryService categories,
                                       IncomeRepository income, ExpenseRepository expenses, Clock clock) {
        this.repo = repo;
        this.categories = categories;
        this.income = income;
        this.expenses = expenses;
        this.clock = clock;
    }

    @Transactional
    public RecurringTransactionResponse create(User user, RecurringTransactionRequest req) {
        String type = req.transactionType().toUpperCase(Locale.ROOT);
        LocalDate start = req.startDate();
        if (start.isBefore(today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Start date cannot be in the past. Add earlier records manually as normal income or expenses.");
        }
        checkEndDate(start, req.endDate());
        RecurringTransaction r = repo.save(RecurringTransaction.builder()
                .user(user)
                .category(categories.requireOfType(req.categoryId(), type))
                .transactionType(type)
                .amount(req.amount())
                .frequency(req.frequency().toUpperCase(Locale.ROOT))
                .startDate(start)
                .nextExecution(start)
                .endDate(req.endDate())
                .description(req.description())
                .build());
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> list(User user, Boolean active) {
        List<RecurringTransaction> found = active == null
                ? repo.findByUserIdOrderByIdDesc(user.getId())
                : repo.findByUserIdAndActiveOrderByIdDesc(user.getId(), active);
        return found.stream().map(RecurringTransactionService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RecurringTransactionResponse get(User user, Long id) {
        return toResponse(owned(user, id));
    }

    @Transactional
    public RecurringTransactionResponse update(User user, Long id, RecurringTransactionRequest req) {
        RecurringTransaction r = owned(user, id);
        String type = req.transactionType().toUpperCase(Locale.ROOT);
        checkEndDate(req.startDate(), req.endDate());

        // Moving the start date restarts the schedule from the new date.
        if (!req.startDate().equals(r.getStartDate())) {
            if (req.startDate().isBefore(today())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A new start date cannot be in the past.");
            }
            r.setStartDate(req.startDate());
            r.setNextExecution(req.startDate());
        }
        r.setTransactionType(type);
        r.setCategory(categories.requireOfType(req.categoryId(), type));
        r.setAmount(req.amount());
        r.setFrequency(req.frequency().toUpperCase(Locale.ROOT));
        r.setEndDate(req.endDate());
        r.setDescription(req.description());
        return toResponse(r);
    }

    @Transactional
    public RecurringTransactionResponse pause(User user, Long id) {
        RecurringTransaction r = owned(user, id);
        r.setActive(false);
        return toResponse(r);
    }

    /** Resuming skips anything that fell due while paused; it does not back-fill. */
    @Transactional
    public RecurringTransactionResponse resume(User user, Long id) {
        RecurringTransaction r = owned(user, id);
        LocalDate next = r.getNextExecution();
        LocalDate today = today();
        while (next.isBefore(today)) {
            next = RecurrenceCalculator.next(next, r.getFrequency(), r.getStartDate());
        }
        if (r.getEndDate() != null && next.isAfter(r.getEndDate())) {
            throw new ApiException(HttpStatus.CONFLICT, "This schedule has ended. Extend its end date first.");
        }
        r.setNextExecution(next);
        r.setActive(true);
        return toResponse(r);
    }

    @Transactional
    public void delete(User user, Long id) {
        repo.delete(owned(user, id)); // records it already created are kept
    }

    /** The income/expense records this schedule has created, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<RecurringRunResponse> history(User user, Long id, int page, int size) {
        RecurringTransaction r = owned(user, id);
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        if ("INCOME".equals(r.getTransactionType())) {
            var pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "incomeDate", "id"));
            return PageResponse.from(income.findByRecurringIdAndUserId(id, user.getId(), pageable)
                    .map(i -> new RecurringRunResponse(i.getId(), i.getIncomeDate(), i.getAmount(), i.getDescription())));
        }
        var pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "expenseDate", "id"));
        return PageResponse.from(expenses.findByRecurringIdAndUserId(id, user.getId(), pageable)
                .map(e -> new RecurringRunResponse(e.getId(), e.getExpenseDate(), e.getAmount(), e.getDescription())));
    }

    // ---------- helpers ----------

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static void checkEndDate(LocalDate start, LocalDate end) {
        if (end != null && end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date cannot be before the start date.");
        }
    }

    private RecurringTransaction owned(User user, Long id) {
        return repo.findById(id)
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recurring transaction not found."));
    }

    static RecurringTransactionResponse toResponse(RecurringTransaction r) {
        boolean ended = r.getEndDate() != null && r.getNextExecution().isAfter(r.getEndDate());
        String status = ended ? "ENDED" : r.isActive() ? "ACTIVE" : "PAUSED";
        return new RecurringTransactionResponse(r.getId(), r.getTransactionType(), r.getCategory().getId(),
                r.getCategory().getName(), r.getAmount(), r.getFrequency(), r.getStartDate(), r.getEndDate(),
                r.getNextExecution(), r.getDescription(), r.isActive(), status);
    }
}
