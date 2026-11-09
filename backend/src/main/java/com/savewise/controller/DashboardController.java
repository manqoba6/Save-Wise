package com.savewise.controller;

import com.savewise.dto.DashboardResponse;
import com.savewise.service.CurrentUserService;
import com.savewise.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;
    private final CurrentUserService currentUser;

    public DashboardController(DashboardService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** ?month=2026-10-01 (any date in the month). Defaults to the current month. */
    @GetMapping
    public DashboardResponse get(Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return service.get(currentUser.get(auth), month);
    }
}
