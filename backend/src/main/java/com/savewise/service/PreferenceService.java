package com.savewise.service;

import com.savewise.dto.CurrencyOptionResponse;
import com.savewise.dto.SettingsRequest;
import com.savewise.dto.SettingsResponse;
import com.savewise.entity.User;
import com.savewise.entity.UserPreference;
import com.savewise.exception.ApiException;
import com.savewise.repository.UserPreferenceRepository;
import com.savewise.util.CurrencyFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Service
public class PreferenceService {
    static final String DEFAULT_THEME = "LIGHT";
    static final String DEFAULT_CURRENCY = "ZAR";

    /** Shown in the settings dropdown. Any valid ISO 4217 code is still accepted by the API. */
    private static final List<String> COMMON_CURRENCIES = List.of(
            "ZAR", "USD", "EUR", "GBP", "AUD", "CAD", "NZD", "JPY", "CNY", "INR",
            "BRL", "NGN", "KES", "GHS", "BWP", "NAD", "ZMW", "MZN", "MUR", "AED");

    private final UserPreferenceRepository repo;

    public PreferenceService(UserPreferenceRepository repo) { this.repo = repo; }

    /** Creates the user's row with defaults on first use. */
    @Transactional
    public SettingsResponse get(User user) {
        return toResponse(getOrCreate(user));
    }

    @Transactional
    public SettingsResponse update(User user, SettingsRequest req) {
        String currency = req.currency().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "'" + currency + "' is not a recognised currency code.");
        }
        UserPreference p = getOrCreate(user);
        p.setTheme(req.theme().toUpperCase(Locale.ROOT));
        p.setCurrency(currency);
        p.setNotificationsEnabled(req.notificationsEnabled());
        return toResponse(p);
    }

    public List<CurrencyOptionResponse> currencies() {
        return COMMON_CURRENCIES.stream()
                .map(code -> new CurrencyOptionResponse(code, Currency.getInstance(code).getDisplayName(Locale.ENGLISH),
                        CurrencyFormatter.symbol(code).trim()))
                .toList();
    }

    // ---- used by other services; they never create rows, they just fall back to defaults ----

    @Transactional(readOnly = true)
    public String currencyFor(User user) {
        return repo.findByUserId(user.getId()).map(UserPreference::getCurrency).orElse(DEFAULT_CURRENCY);
    }

    @Transactional(readOnly = true)
    public boolean notificationsEnabled(User user) {
        return repo.findByUserId(user.getId()).map(UserPreference::isNotificationsEnabled).orElse(true);
    }

    private UserPreference getOrCreate(User user) {
        return repo.findByUserId(user.getId()).orElseGet(() -> repo.save(UserPreference.builder()
                .user(user).theme(DEFAULT_THEME).currency(DEFAULT_CURRENCY).notificationsEnabled(true).build()));
    }

    private static SettingsResponse toResponse(UserPreference p) {
        return new SettingsResponse(p.getTheme(), p.getCurrency(), CurrencyFormatter.symbol(p.getCurrency()).trim(),
                p.isNotificationsEnabled(), p.getUpdatedAt());
    }
}
