package com.savewise.controller;

import com.savewise.dto.CurrencyOptionResponse;
import com.savewise.dto.SettingsRequest;
import com.savewise.dto.SettingsResponse;
import com.savewise.service.CurrentUserService;
import com.savewise.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final PreferenceService service;
    private final CurrentUserService currentUser;

    public SettingsController(PreferenceService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public SettingsResponse get(Authentication auth) {
        return service.get(currentUser.get(auth));
    }

    /** Replaces all three settings (theme, currency, notificationsEnabled). */
    @PutMapping
    public SettingsResponse update(Authentication auth, @Valid @RequestBody SettingsRequest req) {
        return service.update(currentUser.get(auth), req);
    }

    /** Common currencies for the settings dropdown. */
    @GetMapping("/currencies")
    public List<CurrencyOptionResponse> currencies() {
        return service.currencies();
    }
}
