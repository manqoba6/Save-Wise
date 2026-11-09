package com.savewise.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyFormatterTest {
    @Test
    void usesKnownSymbols() {
        assertEquals("R1,234.50", CurrencyFormatter.format(new BigDecimal("1234.5"), "ZAR"));
        assertEquals("$10.00", CurrencyFormatter.format(new BigDecimal("10"), "USD"));
        assertEquals("€0.99", CurrencyFormatter.format(new BigDecimal("0.99"), "EUR"));
    }

    @Test
    void fallsBackToTheCodeForUnknownSymbols() {
        assertEquals("BWP 1,000.00", CurrencyFormatter.format(new BigDecimal("1000"), "BWP"));
    }

    @Test
    void respectsCurrenciesWithoutDecimals() {
        assertEquals("¥1,500", CurrencyFormatter.format(new BigDecimal("1500"), "JPY"));
    }
}
