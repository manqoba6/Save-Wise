package com.savewise.controller;

import com.savewise.dto.*;
import com.savewise.service.CurrentUserService;
import com.savewise.service.IncomeService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/income")
public class IncomeController {
    private final IncomeService service;
    private final CurrentUserService currentUser;

    public IncomeController(IncomeService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<IncomeResponse> create(Authentication auth, @Valid @RequestBody IncomeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(currentUser.get(auth), req));
    }

    /** Search + filter: ?q=salary&categoryId=1&from=2026-01-01&to=2026-01-31&page=0&size=20 */
    @GetMapping
    public PageResponse<IncomeResponse> search(Authentication auth,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(currentUser.get(auth), categoryId, from, to, q, page, size);
    }

    @GetMapping("/{id}")
    public IncomeResponse get(Authentication auth, @PathVariable Long id) {
        return service.get(currentUser.get(auth), id);
    }

    @PutMapping("/{id}")
    public IncomeResponse update(Authentication auth, @PathVariable Long id, @Valid @RequestBody IncomeRequest req) {
        return service.update(currentUser.get(auth), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        service.delete(currentUser.get(auth), id);
        return ResponseEntity.noContent().build();
    }
}
