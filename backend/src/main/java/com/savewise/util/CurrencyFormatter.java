package com.savewise.util;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;

public final class CurrencyFormatter {
    private CurrencyFormatter() {}

    private static final Map<String, String> SYMBOLS = Map.ofEntries(
            Map.entry("ZAR", "R"), Map.entry("USD", "$"), Map.entry("EUR", "€"), Map.entry("GBP", "£"),
            Map.entry("JPY", "¥"), Map.entry("CNY", "¥"), Map.entry("INR", "₹"), Map.entry("NGN", "₦"),
            Map.entry("BRL", "R$"), Map.entry("KES", "KSh"), Map.entry("GHS", "GH₵"),
            Map.entry("AUD", "A$"), Map.entry("CAD", "C$"), Map.entry("NZD", "NZ$"));

    /** A short symbol where we know one, otherwise the code followed by a space (e.g. "BWP "). */
    public static String symbol(String code) {
        String s = SYMBOLS.get(code);
        return s != null ? s : code + " ";
    }

    /** "R1,234.50", "$10.00", "BWP 1,000.00", "¥1,500" (no decimals for currencies that have none). */
    public static String format(BigDecimal amount, String code) {
        int digits = Math.max(0, Currency.getInstance(code).getDefaultFractionDigits());
        return symbol(code) + String.format(Locale.ENGLISH, "%,." + digits + "f", amount);
    }

    /** "ZAR 1,234.50": always the code, for places that cannot draw every currency symbol (PDF). */
    public static String formatWithCode(BigDecimal amount, String code) {
        int digits = Math.max(0, Currency.getInstance(code).getDefaultFractionDigits());
        return code + " " + String.format(Locale.ENGLISH, "%,." + digits + "f", amount);
    }
}
