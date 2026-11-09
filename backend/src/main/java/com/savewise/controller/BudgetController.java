package com.savewise.controller;

import com.savewise.dto.BudgetRequest;
import com.savewise.dto.BudgetResponse;
import com.savewise.service.BudgetService;
import com.savewise.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService service;
    private final CurrentUserService currentUser;

    public BudgetController(BudgetService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(Authentication auth, @Valid @RequestBody BudgetRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(currentUser.get(auth), req));
    }

    /** ?month=2026-10-01 (any date in the month). Defaults to the current month. */
    @GetMapping
    public List<BudgetResponse> list(Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return service.list(currentUser.get(auth), month);
    }

    @GetMapping("/{id}")
    public BudgetResponse get(Authentication auth, @PathVariable Long id) {
        return service.get(currentUser.get(auth), id);
    }

    @PutMapping("/{id}")
    public BudgetResponse update(Authentication auth, @PathVariable Long id, @Valid @RequestBody BudgetRequest req) {
        return service.update(currentUser.get(auth), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        service.delete(currentUser.get(auth), id);
        return ResponseEntity.noContent().build();
    }
}
