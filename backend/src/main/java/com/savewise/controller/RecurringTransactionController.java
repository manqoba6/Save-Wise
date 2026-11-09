package com.savewise.controller;

import com.savewise.dto.*;
import com.savewise.service.CurrentUserService;
import com.savewise.service.RecurringScheduler;
import com.savewise.service.RecurringTransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recurring-transactions")
public class RecurringTransactionController {
    private final RecurringTransactionService service;
    private final RecurringScheduler scheduler;
    private final CurrentUserService currentUser;

    public RecurringTransactionController(RecurringTransactionService service, RecurringScheduler scheduler,
                                          CurrentUserService currentUser) {
        this.service = service;
        this.scheduler = scheduler;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<RecurringTransactionResponse> create(Authentication auth,
            @Valid @RequestBody RecurringTransactionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(currentUser.get(auth), req));
    }

    /** Optional ?active=true or false */
    @GetMapping
    public List<RecurringTransactionResponse> list(Authentication auth, @RequestParam(required = false) Boolean active) {
        return service.list(currentUser.get(auth), active);
    }

    @GetMapping("/{id}")
    public RecurringTransactionResponse get(Authentication auth, @PathVariable Long id) {
        return service.get(currentUser.get(auth), id);
    }

    @PutMapping("/{id}")
    public RecurringTransactionResponse update(Authentication auth, @PathVariable Long id,
            @Valid @RequestBody RecurringTransactionRequest req) {
        return service.update(currentUser.get(auth), id, req);
    }

    @PutMapping("/{id}/pause")
    public RecurringTransactionResponse pause(Authentication auth, @PathVariable Long id) {
        return service.pause(currentUser.get(auth), id);
    }

    @PutMapping("/{id}/resume")
    public RecurringTransactionResponse resume(Authentication auth, @PathVariable Long id) {
        return service.resume(currentUser.get(auth), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        service.delete(currentUser.get(auth), id);
        return ResponseEntity.noContent().build();
    }

    /** Records this schedule has created so far, newest first. */
    @GetMapping("/{id}/history")
    public PageResponse<RecurringRunResponse> history(Authentication auth, @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.history(currentUser.get(auth), id, page, size);
    }

    /** Runs the daily job now. ADMIN only; handy for testing without waiting until 00:05. */
    @PostMapping("/run-due")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> runDue() {
        return Map.of("recordsCreated", scheduler.runDue());
    }
}
