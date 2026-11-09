package com.savewise.service;

import com.savewise.dto.AssistantDto.*;
import com.savewise.dto.BudgetResponse;
import com.savewise.dto.CategorySpend;
import com.savewise.dto.DashboardResponse.HealthScore;
import com.savewise.dto.GoalResponse;
import com.savewise.entity.User;
import com.savewise.repository.ExpenseRepository;
import com.savewise.repository.IncomeRepository;
import com.savewise.repository.SavingAccountRepository;
import com.savewise.service.AnalyticsService.MonthStat;
import com.savewise.util.CurrencyFormatter;
import com.savewise.util.Stats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The smart finance assistant (SRS ch. 3, SDD ch. 11, Backend Guide ch. 16).
 * It is rule-based: every message comes from a visible calculation on the user's own data,
 * with no AI model involved, so the advice is predictable and explainable.
 */
@Service
public class AssistantService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TARGET_SAVINGS_RATE = new BigDecimal("0.20");   // same target as the health score
    private static final BigDecimal SPIKE_FACTOR = new BigDecimal("1.5");           // 50% above usual
    private static final BigDecimal MATERIAL_SHARE = new BigDecimal("0.05");        // ignore categories under 5% of spending
    private static final BigDecimal CUT_SHARE = new BigDecimal("0.15");             // suggest trimming 15%
    private static final int EMERGENCY_FUND_MONTHS = 3;
    private static final int MAX_ADVICE_ITEMS = 12;
    private static final Map<String, Integer> SEVERITY_ORDER = Map.of("CRITICAL", 0, "WARNING", 1, "INFO", 2);
    /** Categories whose name contains one of these are treated as essentials and never suggested for cuts. */
    private static final List<String> ESSENTIAL_WORDS = List.of(
            "rent", "utilit", "health", "educat", "insurance", "mortgage", "loan", "debt");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final AnalyticsService analytics;
    private final BudgetService budgetService;
    private final GoalService goalService;
    private final FinancialHealthService health;
    private final PreferenceService preferences;
    private final IncomeRepository income;
    private final ExpenseRepository expenses;
    private final SavingAccountRepository savingAccounts;

    public AssistantService(AnalyticsService analytics, BudgetService budgetService, GoalService goalService,
                            FinancialHealthService health, PreferenceService preferences, IncomeRepository income,
                            ExpenseRepository expenses, SavingAccountRepository savingAccounts) {
        this.analytics = analytics;
        this.budgetService = budgetService;
        this.goalService = goalService;
        this.health = health;
        this.preferences = preferences;
        this.income = income;
        this.expenses = expenses;
        this.savingAccounts = savingAccounts;
    }

    // =====================================================================
    // Overspending analysis
    // =====================================================================

    @Transactional(readOnly = true)
    public OverspendingResponse overspending(User user, LocalDate monthParam) {
        LocalDate today = analytics.today();
        LocalDate month = (monthParam == null ? today : monthParam).withDayOfMonth(1);
        LocalDate end = month.withDayOfMonth(month.lengthOfMonth());
        boolean isCurrent = YearMonth.from(month).equals(YearMonth.from(today));
        String cur = preferences.currencyFor(user);
        Long uid = user.getId();
        String label = month.format(MONTH_LABEL);

        BigDecimal monthIncome = income.sumBetween(uid, month, end);
        BigDecimal monthExpenses = expenses.sumBetween(uid, month, end);

        List<Insight> findings = new ArrayList<>();
        Set<String> flagged = new HashSet<>();

        // 1. Budgets (SDD ch. 11: 80% warning, 100% critical), plus whether this month's pace will break one.
        for (BudgetResponse b : budgetService.list(user, month)) {
            String name = b.categoryName();
            if ("CRITICAL".equals(b.status())) {
                findings.add(insight("BUDGET_EXCEEDED", "CRITICAL", "Over budget: " + name,
                        "You have spent " + money(b.spent(), cur) + " of your " + money(b.budgetAmount(), cur) + " "
                                + name + " budget (" + b.percentUsed() + "%).",
                        name, b.spent().subtract(b.budgetAmount())));
                flagged.add(name);
            } else if ("WARNING".equals(b.status())) {
                findings.add(insight("BUDGET_NEAR_LIMIT", "WARNING", "Close to your " + name + " budget",
                        "You have used " + b.percentUsed() + "% of your " + name + " budget; "
                                + money(b.remaining(), cur) + " is left.",
                        name, b.remaining()));
                flagged.add(name);
            } else if (isCurrent && today.getDayOfMonth() >= 5) {
                BigDecimal projected = b.spent().multiply(BigDecimal.valueOf(month.lengthOfMonth()))
                        .divide(BigDecimal.valueOf(today.getDayOfMonth()), 2, RoundingMode.HALF_UP);
                if (projected.compareTo(b.budgetAmount()) >= 0) {
                    findings.add(insight("BUDGET_PACE", "WARNING", "On pace to overspend: " + name,
                            "At your current pace you will spend about " + money(projected, cur)
                                    + " on " + name + " by the end of the month, against a budget of "
                                    + money(b.budgetAmount(), cur) + ".",
                            name, projected.subtract(b.budgetAmount())));
                    flagged.add(name);
                }
            }
        }

        // 2. Categories running well above their usual level (usual = average of the 3 months before).
        findings.addAll(spikes(uid, month, end, cur, flagged));

        // 3. Spending more than was earned.
        if (monthExpenses.signum() > 0 && monthExpenses.compareTo(monthIncome) > 0) {
            if (monthIncome.signum() > 0) {
                BigDecimal over = monthExpenses.subtract(monthIncome);
                findings.add(insight("EXPENSES_EXCEED_INCOME", "CRITICAL", "Spending is higher than income",
                        "You have spent " + money(over, cur) + " more than you earned"
                                + (isCurrent ? " so far this month." : " in " + label + "."),
                        null, over));
            } else {
                findings.add(insight("EXPENSES_EXCEED_INCOME", "INFO", "No income recorded",
                        "You have recorded " + money(monthExpenses, cur) + " of expenses and no income in " + label + ".",
                        null, monthExpenses));
            }
        }

        if (findings.isEmpty()) {
            findings.add(insight("ALL_CLEAR", "INFO", "No overspending detected",
                    "Nothing looks out of line for " + label + ".", null, null));
        }
        sortBySeverity(findings);
        return new OverspendingResponse(month, cur, monthIncome, monthExpenses, findings);
    }

    private List<Insight> spikes(Long uid, LocalDate month, LocalDate end, String cur, Set<String> flagged) {
        // Per-category averages over the (up to) 3 previous months that had any spending.
        Map<String, BigDecimal> priorTotals = new HashMap<>();
        BigDecimal priorOverall = BigDecimal.ZERO;
        int monthsWithData = 0;
        for (int i = 1; i <= 3; i++) {
            LocalDate from = month.minusMonths(i);
            List<CategorySpend> spend = expenses.spendByCategory(uid, from, from.withDayOfMonth(from.lengthOfMonth()));
            if (spend.isEmpty()) continue;
            monthsWithData++;
            for (CategorySpend c : spend) {
                priorTotals.merge(c.categoryName(), c.amount(), BigDecimal::add);
                priorOverall = priorOverall.add(c.amount());
            }
        }
        List<Insight> out = new ArrayList<>();
        if (monthsWithData == 0) return out;
        BigDecimal divisor = BigDecimal.valueOf(monthsWithData);
        BigDecimal materialFloor = priorOverall.divide(divisor, 2, RoundingMode.HALF_UP).multiply(MATERIAL_SHARE);

        for (CategorySpend c : expenses.spendByCategory(uid, month, end)) {
            if (flagged.contains(c.categoryName()) || !priorTotals.containsKey(c.categoryName())) continue;
            BigDecimal usual = priorTotals.get(c.categoryName()).divide(divisor, 2, RoundingMode.HALF_UP);
            boolean wellAbove = usual.signum() > 0 && c.amount().compareTo(usual.multiply(SPIKE_FACTOR)) >= 0;
            boolean material = c.amount().subtract(usual).compareTo(materialFloor) >= 0;
            if (wellAbove && material) {
                BigDecimal pct = c.amount().multiply(HUNDRED).divide(usual, 0, RoundingMode.HALF_UP).subtract(HUNDRED);
                out.add(insight("SPENDING_SPIKE", "WARNING", c.categoryName() + " spending is unusually high",
                        c.categoryName() + " spending is " + money(c.amount(), cur) + ", about " + pct
                                + "% above your usual " + money(usual, cur) + " a month.",
                        c.categoryName(), c.amount().subtract(usual)));
            }
        }
        return out;
    }

    // =====================================================================
    // Saving recommendations
    // =====================================================================

    @Transactional(readOnly = true)
    public RecommendationsResponse savingRecommendations(User user) {
        String cur = preferences.currencyFor(user);
        List<MonthStat> window = analytics.completedMonths(user, 6);
        if (window.isEmpty()) {
            return new RecommendationsResponse(cur, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                    List.of(insight("NOT_ENOUGH_DATA", "INFO", "Not enough history yet",
                            "Record at least one full month of income and expenses and I will start suggesting ways to save.",
                            null, null)));
        }

        int n = window.size();
        BigDecimal avgIncome = Stats.mean(window.stream().map(MonthStat::income).toList());
        BigDecimal avgExpenses = Stats.mean(window.stream().map(MonthStat::expenses).toList());
        BigDecimal avgNet = avgIncome.subtract(avgExpenses);
        BigDecimal rate = avgIncome.signum() > 0
                ? avgNet.multiply(HUNDRED).divide(avgIncome, 1, RoundingMode.HALF_UP) : null;

        // Average monthly spend per category across the window.
        Map<String, BigDecimal> categoryAverage = new HashMap<>();
        for (Map<String, BigDecimal> month : analytics.categorySpendByMonth(user, window)) {
            month.forEach((name, amount) -> categoryAverage.merge(name, amount, BigDecimal::add));
        }
        categoryAverage.replaceAll((name, total) -> total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
        BigDecimal materialFloor = avgExpenses.multiply(MATERIAL_SHARE);
        List<Map.Entry<String, BigDecimal>> bySpend = categoryAverage.entrySet().stream()
                .filter(e -> e.getValue().compareTo(materialFloor) >= 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).toList();

        List<Insight> recs = new ArrayList<>();
        boolean needMoreSavings = avgNet.signum() < 0
                || (avgIncome.signum() > 0 && avgNet.compareTo(avgIncome.multiply(TARGET_SAVINGS_RATE)) < 0);

        // 1. The headline: deficit, or a gap to the 20% savings target.
        if (avgNet.signum() < 0) {
            BigDecimal deficit = avgNet.negate();
            recs.add(insight("SAVE_MORE", "CRITICAL", "You are spending more than you earn",
                    "Over the last " + monthsText(n) + " you spent about " + money(deficit, cur)
                            + " more than you earned each month. Cutting at least that much is the first priority.",
                    null, Stats.niceRound(deficit)));
        } else if (needMoreSavings) {
            BigDecimal gap = avgIncome.multiply(TARGET_SAVINGS_RATE).subtract(avgNet);
            BigDecimal suggested = Stats.niceRound(gap);
            recs.add(insight("SAVE_MORE", rate.compareTo(BigDecimal.TEN) < 0 ? "WARNING" : "INFO",
                    "Increase your savings",
                    "You keep about " + rate + "% of your income. Setting aside " + money(suggested, cur)
                            + " more each month would take you to the 20% most advisers recommend.",
                    null, suggested));
        }

        // 2. Where to find it: the two biggest non-essential categories.
        if (needMoreSavings) {
            bySpend.stream().filter(e -> !isEssential(e.getKey())).limit(2).forEach(e -> {
                BigDecimal trim = e.getValue().multiply(CUT_SHARE).setScale(0, RoundingMode.HALF_UP);
                recs.add(insight("REDUCE_CATEGORY", "INFO", "Reduce " + e.getKey() + " spending",
                        "You spend about " + money(e.getValue(), cur) + " a month on " + e.getKey()
                                + ". Trimming 15% would free up " + money(trim, cur) + " a month.",
                        e.getKey(), trim));
            });
        }

        // 3. Emergency fund: three months of spending is the usual target.
        BigDecimal savings = savingAccounts.sumBalances(user.getId());
        if (avgExpenses.signum() > 0) {
            BigDecimal target = avgExpenses.multiply(BigDecimal.valueOf(EMERGENCY_FUND_MONTHS));
            if (savings.compareTo(target) < 0) {
                BigDecimal perMonth = Stats.niceRound(target.subtract(savings).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP));
                if (savings.signum() == 0) {
                    recs.add(insight("EMERGENCY_FUND", "WARNING", "Start an emergency fund",
                            "You have no savings set aside. A common target is 3 months of spending (about "
                                    + money(target, cur) + "). Saving " + money(perMonth, cur)
                                    + " a month would get you there in a year.",
                            null, perMonth));
                } else {
                    BigDecimal covered = savings.divide(avgExpenses, 1, RoundingMode.HALF_UP);
                    recs.add(insight("EMERGENCY_FUND", "INFO", "Build up your emergency fund",
                            "Your savings cover about " + covered + " months of spending. Aim for 3 ("
                                    + money(target, cur) + "); " + money(perMonth, cur) + " a month gets you there in a year.",
                            null, perMonth));
                }
            }
        }

        // 4. Goals that the current surplus cannot reach by their deadline.
        List<GoalForecast> goals = analytics.goalForecasts(user, avgNet);
        for (GoalForecast g : goals) {
            if (!g.onTrack() && g.requiredMonthly() != null) {
                recs.add(insight("GOAL_OFF_TRACK", "WARNING", "Goal at risk: " + g.title(),
                        "\"" + g.title() + "\" needs " + money(g.requiredMonthly(), cur) + " a month to be reached by "
                                + g.deadline() + ", but your average monthly surplus is " + money(avgNet.max(BigDecimal.ZERO), cur) + ".",
                        null, g.requiredMonthly()));
            }
        }
        if (goals.isEmpty() && avgNet.signum() > 0) {
            recs.add(insight("SET_GOAL", "INFO", "Give your savings a purpose",
                    "You have a surplus of about " + money(avgNet, cur)
                            + " a month. Setting a goal (a holiday, a laptop, a deposit) makes it easier to stick to.",
                    null, avgNet));
        }

        // 5. Categories with no budget this month.
        Set<String> budgeted = new HashSet<>();
        budgetService.list(user, analytics.today()).forEach(b -> budgeted.add(b.categoryName()));
        bySpend.stream().filter(e -> !budgeted.contains(e.getKey())).limit(3).forEach(e -> {
            BigDecimal suggestion = Stats.niceRound(e.getValue());
            recs.add(insight("SET_BUDGET", "INFO", "Set a budget for " + e.getKey(),
                    "You spend about " + money(e.getValue(), cur) + " a month on " + e.getKey()
                            + ". A budget of around " + money(suggestion, cur) + " lets SaveWise warn you before you overspend.",
                    e.getKey(), suggestion));
        });

        sortBySeverity(recs);
        return new RecommendationsResponse(cur, n, avgIncome, avgExpenses, avgNet, rate, recs);
    }

    // =====================================================================
    // Spending trends
    // =====================================================================

    @Transactional(readOnly = true)
    public TrendsResponse trends(User user, int monthsParam) {
        int months = Math.min(Math.max(monthsParam, 2), 12);
        String cur = preferences.currencyFor(user);
        List<MonthStat> window = analytics.completedMonths(user, months);
        int n = window.size();

        List<MonthPoint> points = window.stream().map(m -> new MonthPoint(m.month().toString(), m.income(), m.expenses(),
                m.net(), m.income().signum() > 0
                        ? m.net().multiply(HUNDRED).divide(m.income(), 1, RoundingMode.HALF_UP) : null)).toList();

        if (n < 2) {
            return new TrendsResponse(cur, n, points, "INSUFFICIENT_DATA", BigDecimal.ZERO,
                    "INSUFFICIENT_DATA", BigDecimal.ZERO, List.of(),
                    "Trends need at least two full months of history. Keep recording and check back next month.");
        }

        List<BigDecimal> incomes = window.stream().map(MonthStat::income).toList();
        List<BigDecimal> outgoings = window.stream().map(MonthStat::expenses).toList();
        BigDecimal incomePct = Stats.slopePercentOfMean(incomes);
        BigDecimal expensePct = Stats.slopePercentOfMean(outgoings);

        // Category trends: last full month against the average of the months before it.
        List<Map<String, BigDecimal>> byMonth = analytics.categorySpendByMonth(user, window);
        Map<String, BigDecimal> totals = new HashMap<>();
        byMonth.forEach(m -> m.forEach((name, amt) -> totals.merge(name, amt, BigDecimal::add)));
        Map<String, BigDecimal> latest = byMonth.get(n - 1);

        List<CategoryTrend> categories = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(8)
                .map(e -> {
                    BigDecimal prior = BigDecimal.ZERO;
                    for (int i = 0; i < n - 1; i++) prior = prior.add(byMonth.get(i).getOrDefault(e.getKey(), BigDecimal.ZERO));
                    BigDecimal previousAvg = prior.divide(BigDecimal.valueOf(n - 1), 2, RoundingMode.HALF_UP);
                    BigDecimal last = latest.getOrDefault(e.getKey(), BigDecimal.ZERO);
                    BigDecimal change = Stats.changePercent(last, previousAvg);
                    String direction = change == null ? (last.signum() > 0 ? "NEW" : "STABLE")
                            : change.compareTo(BigDecimal.TEN) >= 0 ? "RISING"
                            : change.compareTo(BigDecimal.TEN.negate()) <= 0 ? "FALLING" : "STABLE";
                    return new CategoryTrend(e.getKey(), last, previousAvg, change, direction);
                }).toList();

        return new TrendsResponse(cur, n, points,
                Stats.direction(incomePct, n), incomePct, Stats.direction(expensePct, n), expensePct, categories,
                n < 3 ? "Only " + n + " full months so far, so direction labels will appear once there are 3." : null);
    }

    // =====================================================================
    // Combined advice feed
    // =====================================================================

    @Transactional(readOnly = true)
    public AdviceResponse advice(User user, LocalDate monthParam) {
        LocalDate today = analytics.today();
        LocalDate month = (monthParam == null ? today : monthParam).withDayOfMonth(1);
        LocalDate end = month.withDayOfMonth(month.lengthOfMonth());
        String cur = preferences.currencyFor(user);

        OverspendingResponse over = overspending(user, month);
        List<BudgetResponse> budgets = budgetService.list(user, month);
        List<GoalResponse> activeGoals = goalService.list(user, "ACTIVE");
        HealthScore score = health.calculate(user, month, over.income(), over.expenses(), budgets, activeGoals);

        List<Insight> all = new ArrayList<>();
        over.findings().stream().filter(i -> !"ALL_CLEAR".equals(i.type())).forEach(all::add);
        savingRecommendations(user).recommendations().stream()
                .filter(i -> !"NOT_ENOUGH_DATA".equals(i.type())).forEach(all::add);
        all.addAll(trendInsights(trends(user, 6), cur));

        if (all.isEmpty()) {
            all.add(over.expenses().signum() == 0 && over.income().signum() == 0
                    ? insight("NOT_ENOUGH_DATA", "INFO", "Nothing to analyse yet",
                            "Add some income and expenses and I will start giving advice.", null, null)
                    : insight("ALL_CLEAR", "INFO", "You are on track", "No problems spotted. Keep it up!", null, null));
        }
        sortBySeverity(all);
        return new AdviceResponse(month, cur, score, all.size() > MAX_ADVICE_ITEMS ? all.subList(0, MAX_ADVICE_ITEMS) : all);
    }

    private List<Insight> trendInsights(TrendsResponse t, String cur) {
        List<Insight> out = new ArrayList<>();
        if ("RISING".equals(t.expenseTrend())) {
            out.add(insight("TREND_SPENDING", t.expenseChangePerMonthPercent().compareTo(new BigDecimal("5")) >= 0 ? "WARNING" : "INFO",
                    "Your spending is trending up",
                    "Your monthly spending has been rising by about " + t.expenseChangePerMonthPercent()
                            + "% a month over the last " + monthsText(t.monthsAnalysed()) + ".", null, null));
        } else if ("FALLING".equals(t.expenseTrend())) {
            out.add(insight("TREND_SPENDING", "INFO", "Your spending is trending down",
                    "Your monthly spending has been falling by about " + t.expenseChangePerMonthPercent().abs()
                            + "% a month. Nice work.", null, null));
        }
        if ("FALLING".equals(t.incomeTrend())) {
            out.add(insight("TREND_SPENDING", "WARNING", "Your income is trending down",
                    "Your monthly income has been falling by about " + t.incomeChangePerMonthPercent().abs()
                            + "% a month. Check that nothing is missing from your records.", null, null));
        }
        t.categories().stream()
                .filter(c -> "RISING".equals(c.direction()) && c.changePercent() != null
                        && c.changePercent().compareTo(new BigDecimal("25")) >= 0)
                .limit(2)
                .forEach(c -> out.add(insight("TREND_CATEGORY", "INFO", c.categoryName() + " is creeping up",
                        "You spent " + money(c.lastMonth(), cur) + " on " + c.categoryName() + " last month, "
                                + c.changePercent() + "% more than the " + money(c.previousAverage(), cur)
                                + " a month before.", c.categoryName(), c.lastMonth().subtract(c.previousAverage()))));
        return out;
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private static boolean isEssential(String categoryName) {
        String n = categoryName.toLowerCase(Locale.ROOT);
        return ESSENTIAL_WORDS.stream().anyMatch(n::contains);
    }

    private static void sortBySeverity(List<Insight> list) {
        list.sort(Comparator.comparingInt(i -> SEVERITY_ORDER.getOrDefault(i.severity(), 3))); // stable
    }

    private static String monthsText(int n) {
        return n == 1 ? "month" : n + " months";
    }

    private static String money(BigDecimal v, String cur) {
        return CurrencyFormatter.format(v, cur);
    }

    private static Insight insight(String type, String severity, String title, String message,
                                   String category, BigDecimal amount) {
        return new Insight(type, severity, title, message, category, amount);
    }
}
