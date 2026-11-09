package com.savewise.service;

import com.savewise.dto.BudgetResponse;
import com.savewise.dto.ReportResponse;
import com.savewise.dto.ReportResponse.*;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.ExpenseRepository;
import com.savewise.repository.IncomeRepository;
import com.savewise.repository.SavingTransactionRepository;
import com.savewise.util.ReportPeriod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reports are generated on demand from the user's income and expense records
 * (SDD ch. 12: nothing is stored). Everything is computed from one fetch so that
 * the totals, categories, breakdown and transaction list always agree.
 */
@Service
public class ReportService {
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final IncomeRepository income;
    private final ExpenseRepository expenses;
    private final SavingTransactionRepository savingTxs;
    private final BudgetService budgetService;
    private final PreferenceService preferences;
    private final Clock clock;

    public ReportService(IncomeRepository income, ExpenseRepository expenses, SavingTransactionRepository savingTxs,
                         BudgetService budgetService, PreferenceService preferences, Clock clock) {
        this.income = income;
        this.expenses = expenses;
        this.savingTxs = savingTxs;
        this.budgetService = budgetService;
        this.preferences = preferences;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportResponse generate(User user, String typeParam, LocalDate dateParam) {
        ReportPeriod.Type type;
        try {
            type = ReportPeriod.parseType(typeParam);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        LocalDate reference = dateParam == null ? LocalDate.now(clock) : dateParam;
        ReportPeriod period = ReportPeriod.of(type, reference);
        Long uid = user.getId();

        List<TransactionLine> lines = new ArrayList<>();
        income.findByUserIdAndIncomeDateBetweenOrderByIncomeDateAscIdAsc(uid, period.from(), period.to())
                .forEach(i -> lines.add(new TransactionLine(i.getIncomeDate(), "INCOME",
                        i.getCategory().getName(), i.getDescription(), i.getAmount())));
        expenses.findByUserIdAndExpenseDateBetweenOrderByExpenseDateAscIdAsc(uid, period.from(), period.to())
                .forEach(e -> lines.add(new TransactionLine(e.getExpenseDate(), "EXPENSE",
                        e.getCategory().getName(), e.getDescription(), e.getAmount())));
        lines.sort(Comparator.comparing(TransactionLine::date)); // stable: same-day order is kept

        BigDecimal totalIncome = total(lines, "INCOME");
        BigDecimal totalExpenses = total(lines, "EXPENSE");
        BigDecimal net = totalIncome.subtract(totalExpenses);
        BigDecimal rate = totalIncome.signum() > 0
                ? net.multiply(BigDecimal.valueOf(100)).divide(totalIncome, 1, RoundingMode.HALF_UP) : null;

        Summary summary = new Summary(totalIncome, totalExpenses, net, rate,
                savingTxs.sumByType(uid, "DEPOSIT", period.from(), period.to()),
                savingTxs.sumByType(uid, "WITHDRAW", period.from(), period.to()),
                lines.size());

        List<BudgetResponse> budgets = type == ReportPeriod.Type.MONTHLY
                ? budgetService.list(user, period.from()) : List.of();

        return new ReportResponse(type.name(), period.label(), period.from(), period.to(),
                preferences.currencyFor(user), summary,
                byCategory(lines, "INCOME", totalIncome), byCategory(lines, "EXPENSE", totalExpenses),
                breakdown(period, lines), budgets, lines);
    }

    private static BigDecimal total(List<TransactionLine> lines, String type) {
        return lines.stream().filter(l -> l.type().equals(type))
                .map(TransactionLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<CategoryLine> byCategory(List<TransactionLine> lines, String type, BigDecimal total) {
        Map<String, BigDecimal> sums = new HashMap<>();
        for (TransactionLine l : lines) {
            if (l.type().equals(type)) sums.merge(l.categoryName(), l.amount(), BigDecimal::add);
        }
        return sums.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new CategoryLine(e.getKey(), e.getValue(), total.signum() == 0 ? BigDecimal.ZERO
                        : e.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP)))
                .toList();
    }

    /** Per day for weekly/monthly reports, per month for annual ones. Empty periods still appear, as zeros. */
    private static List<BreakdownLine> breakdown(ReportPeriod period, List<TransactionLine> lines) {
        if (period.type() == ReportPeriod.Type.DAILY) return List.of();
        boolean byMonth = period.type() == ReportPeriod.Type.ANNUAL;

        Map<LocalDate, BigDecimal[]> buckets = new LinkedHashMap<>();
        for (LocalDate d = period.from(); !d.isAfter(period.to()); d = byMonth ? d.plusMonths(1) : d.plusDays(1)) {
            buckets.put(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (TransactionLine l : lines) {
            BigDecimal[] b = buckets.get(byMonth ? l.date().withDayOfMonth(1) : l.date());
            int i = l.type().equals("INCOME") ? 0 : 1;
            b[i] = b[i].add(l.amount());
        }
        List<BreakdownLine> out = new ArrayList<>();
        buckets.forEach((d, b) -> out.add(new BreakdownLine(
                d.format(byMonth ? MONTH_LABEL : DAY_LABEL), b[0], b[1], b[0].subtract(b[1]))));
        return out;
    }
}
