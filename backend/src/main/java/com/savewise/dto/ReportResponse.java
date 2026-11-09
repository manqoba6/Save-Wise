package com.savewise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * type is DAILY, WEEKLY, MONTHLY or ANNUAL. budgets are filled for MONTHLY reports only.
 * breakdown is per day (weekly, monthly) or per month (annual); empty for daily.
 */
public record ReportResponse(
        String type,
        String label,
        LocalDate from,
        LocalDate to,
        String currency,
        Summary summary,
        List<CategoryLine> incomeByCategory,
        List<CategoryLine> expensesByCategory,
        List<BreakdownLine> breakdown,
        List<BudgetResponse> budgets,
        List<TransactionLine> transactions) {

    /** savingsRatePercent is null when there is no income in the period. Savings figures exclude transfers. */
    public record Summary(BigDecimal totalIncome, BigDecimal totalExpenses, BigDecimal net,
                          BigDecimal savingsRatePercent, BigDecimal savingsDeposits, BigDecimal savingsWithdrawals,
                          int transactionCount) {
    }

    public record CategoryLine(String categoryName, BigDecimal amount, BigDecimal percentOfTotal) {
    }

    public record BreakdownLine(String label, BigDecimal income, BigDecimal expenses, BigDecimal net) {
    }

    /** type is INCOME or EXPENSE; amount is always positive. */
    public record TransactionLine(LocalDate date, String type, String categoryName, String description, BigDecimal amount) {
    }
}
