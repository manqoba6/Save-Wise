package com.savewise.controller;

import com.savewise.dto.*;
import com.savewise.service.CurrentUserService;
import com.savewise.service.SavingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/savings")
public class SavingsController {
    private final SavingsService service;
    private final CurrentUserService currentUser;

    public SavingsController(SavingsService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    // ----- accounts -----
    @PostMapping("/accounts")
    public ResponseEntity<SavingAccountResponse> createAccount(Authentication auth, @Valid @RequestBody SavingAccountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAccount(currentUser.get(auth), req));
    }

    @GetMapping("/accounts")
    public List<SavingAccountResponse> listAccounts(Authentication auth) {
        return service.listAccounts(currentUser.get(auth));
    }

    @GetMapping("/accounts/{id}")
    public SavingAccountResponse getAccount(Authentication auth, @PathVariable Long id) {
        return service.getAccount(currentUser.get(auth), id);
    }

    @PutMapping("/accounts/{id}")
    public SavingAccountResponse updateAccount(Authentication auth, @PathVariable Long id, @Valid @RequestBody SavingAccountRequest req) {
        return service.updateAccount(currentUser.get(auth), id, req);
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(Authentication auth, @PathVariable Long id) {
        service.deleteAccount(currentUser.get(auth), id);
        return ResponseEntity.noContent().build();
    }

    // ----- money movement (paths from the Backend Guide) -----
    @PostMapping("/deposit")
    public SavingsOperationResponse deposit(Authentication auth, @Valid @RequestBody SavingsTransactionRequest req) {
        return service.deposit(currentUser.get(auth), req);
    }

    @PostMapping("/withdraw")
    public SavingsOperationResponse withdraw(Authentication auth, @Valid @RequestBody SavingsTransactionRequest req) {
        return service.withdraw(currentUser.get(auth), req);
    }

    @PostMapping("/transfer")
    public TransferResponse transfer(Authentication auth, @Valid @RequestBody TransferRequest req) {
        return service.transfer(currentUser.get(auth), req);
    }

    /** ?accountId= is optional; without it you get history across all your accounts. */
    @GetMapping("/history")
    public PageResponse<SavingTransactionResponse> history(Authentication auth,
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(currentUser.get(auth), accountId, page, size);
    }
}
