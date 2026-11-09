package com.savewise.service;

import com.savewise.dto.AssistantDto.GoalForecast;
import com.savewise.dto.AssistantDto.PredictionResponse;
import com.savewise.dto.AssistantDto.Projection;
import com.savewise.entity.User;
import com.savewise.repository.ExpenseRepository;
import com.savewise.repository.IncomeRepository;
import com.savewise.repository.SavingAccountRepository;
import com.savewise.service.AnalyticsService.MonthStat;
import com.savewise.util.Stats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Forecasts balance and savings 1, 3, 6 and 12 months ahead (Backend Guide ch. 17: "using average income,
 * average expenses and average savings").
 *
 * Method: take the average monthly net (income - expenses) over up to six completed months and project it
 * forward from today's balance. The range shown around each balance is one standard deviation of the
 * monthly net, scaled by the square root of the months ahead: the further out, the wider the range.
 * Income and spending trends are reported alongside, but deliberately not extrapolated: a straight line
 * through a handful of months says little about a year from now.
 */
@Service
public class PredictionService {
    private static final int WINDOW_MONTHS = 6;
    private static final int[] HORIZONS = {1, 3, 6, 12};

    private final AnalyticsService analytics;
    private final IncomeRepository income;
    private final ExpenseRepository expenses;
    private final SavingAccountRepository savingAccounts;
    private final PreferenceService preferences;

    public PredictionService(AnalyticsService analytics, IncomeRepository income, ExpenseRepository expenses,
                             SavingAccountRepository savingAccounts, PreferenceService preferences) {
        this.analytics = analytics;
        this.income = income;
        this.expenses = expenses;
        this.savingAccounts = savingAccounts;
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public PredictionResponse predict(User user) {
        String cur = preferences.currencyFor(user);
        BigDecimal balance = income.sumAll(user.getId()).subtract(expenses.sumAll(user.getId()));
        BigDecimal savings = savingAccounts.sumBalances(user.getId());
        List<MonthStat> window = analytics.completedMonths(user, WINDOW_MONTHS);

        if (window.isEmpty()) {
            return new PredictionResponse(cur, false, "NONE", 0,
                    "Predictions need at least one full month of income and expense history. "
                            + "Keep recording and check back next month.",
                    balance, savings, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    "INSUFFICIENT_DATA", "INSUFFICIENT_DATA", List.of(), List.of());
        }

        int n = window.size();
        List<BigDecimal> incomes = window.stream().map(MonthStat::income).toList();
        List<BigDecimal> outgoings = window.stream().map(MonthStat::expenses).toList();
        List<BigDecimal> nets = window.stream().map(MonthStat::net).toList();
        List<BigDecimal> contributions = window.stream().map(MonthStat::savingsNet).toList();

        BigDecimal avgIncome = Stats.mean(incomes);
        BigDecimal avgExpenses = Stats.mean(outgoings);
        BigDecimal avgNet = Stats.mean(nets);
        BigDecimal avgContribution = Stats.mean(contributions);
        BigDecimal spread = Stats.stdDev(nets);

        List<Projection> projections = new ArrayList<>();
        YearMonth now = YearMonth.from(analytics.today());
        for (int k : HORIZONS) {
            BigDecimal projected = balance.add(avgNet.multiply(BigDecimal.valueOf(k)));
            BigDecimal band = spread.multiply(BigDecimal.valueOf(Math.sqrt(k))).setScale(2, RoundingMode.HALF_UP);
            BigDecimal projectedSavings = savings.add(avgContribution.multiply(BigDecimal.valueOf(k)))
                    .max(BigDecimal.ZERO); // a savings balance cannot go below zero
            projections.add(new Projection(k, now.plusMonths(k).toString(), projected,
                    projected.subtract(band), projected.add(band), projectedSavings));
        }

        String confidence = n >= 6 ? "HIGH" : n >= 3 ? "MEDIUM" : "LOW";
        String message = n >= 6
                ? "Based on the average of your last " + n + " full months."
                : "Based on only " + n + " full month" + (n == 1 ? "" : "s")
                + " of history, so treat these figures as a rough guide.";
        List<GoalForecast> goals = analytics.goalForecasts(user, avgNet);

        return new PredictionResponse(cur, true, confidence, n, message, balance, savings,
                avgIncome, avgExpenses, avgNet, avgContribution,
                Stats.direction(Stats.slopePercentOfMean(incomes), n),
                Stats.direction(Stats.slopePercentOfMean(outgoings), n),
                projections, goals);
    }
}
