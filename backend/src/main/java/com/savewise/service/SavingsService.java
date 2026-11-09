package com.savewise.service;

import com.savewise.dto.*;
import com.savewise.entity.SavingAccount;
import com.savewise.entity.SavingTransaction;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.SavingAccountRepository;
import com.savewise.repository.SavingTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class SavingsService {
    private final SavingAccountRepository accounts;
    private final SavingTransactionRepository txs;

    public SavingsService(SavingAccountRepository accounts, SavingTransactionRepository txs) {
        this.accounts = accounts;
        this.txs = txs;
    }

    // ---------- accounts ----------

    @Transactional
    public SavingAccountResponse createAccount(User user, SavingAccountRequest req) {
        SavingAccount a = accounts.save(SavingAccount.builder()
                .user(user).accountName(req.accountName().trim()).targetAmount(req.targetAmount()).build());
        return toResponse(a);
    }

    @Transactional(readOnly = true)
    public List<SavingAccountResponse> listAccounts(User user) {
        return accounts.findByUserIdOrderByIdAsc(user.getId()).stream().map(SavingsService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SavingAccountResponse getAccount(User user, Long id) {
        return toResponse(owned(user, id));
    }

    @Transactional
    public SavingAccountResponse updateAccount(User user, Long id, SavingAccountRequest req) {
        SavingAccount a = owned(user, id);
        a.setAccountName(req.accountName().trim());
        a.setTargetAmount(req.targetAmount());
        return toResponse(a);
    }

    @Transactional
    public void deleteAccount(User user, Long id) {
        SavingAccount a = owned(user, id);
        if (a.getCurrentBalance().signum() != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Withdraw or transfer the remaining balance before deleting this account.");
        }
        accounts.delete(a);
    }

    // ---------- money movement ----------

    @Transactional
    public SavingsOperationResponse deposit(User user, SavingsTransactionRequest req) {
        SavingAccount a = lockOwned(user, req.accountId());
        a.setCurrentBalance(a.getCurrentBalance().add(req.amount()));
        SavingTransaction t = record(a, "DEPOSIT", req.amount(), req.transactionDate(), req.description());
        return new SavingsOperationResponse(toResponse(t), toResponse(a));
    }

    @Transactional
    public SavingsOperationResponse withdraw(User user, SavingsTransactionRequest req) {
        SavingAccount a = lockOwned(user, req.accountId());
        requireFunds(a, req.amount());
        a.setCurrentBalance(a.getCurrentBalance().subtract(req.amount()));
        SavingTransaction t = record(a, "WITHDRAW", req.amount(), req.transactionDate(), req.description());
        return new SavingsOperationResponse(toResponse(t), toResponse(a));
    }

    @Transactional
    public TransferResponse transfer(User user, TransferRequest req) {
        if (req.fromAccountId().equals(req.toAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose two different accounts to transfer between.");
        }
        // Lock in id order so two opposite transfers can never deadlock each other.
        boolean fromFirst = req.fromAccountId() < req.toAccountId();
        SavingAccount first = lockOwned(user, fromFirst ? req.fromAccountId() : req.toAccountId());
        SavingAccount second = lockOwned(user, fromFirst ? req.toAccountId() : req.fromAccountId());
        SavingAccount from = fromFirst ? first : second;
        SavingAccount to = fromFirst ? second : first;

        requireFunds(from, req.amount());
        from.setCurrentBalance(from.getCurrentBalance().subtract(req.amount()));
        to.setCurrentBalance(to.getCurrentBalance().add(req.amount()));

        String note = req.description() == null || req.description().isBlank() ? "" : " - " + req.description().trim();
        record(from, "TRANSFER", req.amount(), req.transactionDate(), "Transfer to " + to.getAccountName() + note);
        record(to, "TRANSFER", req.amount(), req.transactionDate(), "Transfer from " + from.getAccountName() + note);
        return new TransferResponse(toResponse(from), toResponse(to));
    }

    @Transactional(readOnly = true)
    public PageResponse<SavingTransactionResponse> history(User user, Long accountId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "transactionDate", "id"));
        Page<SavingTransaction> found;
        if (accountId == null) {
            found = txs.findAllForUser(user.getId(), pageable);
        } else {
            owned(user, accountId); // 404 if it is not theirs
            found = txs.findForAccount(user.getId(), accountId, pageable);
        }
        return PageResponse.from(found.map(SavingsService::toResponse));
    }

    // ---------- helpers ----------

    private void requireFunds(SavingAccount a, BigDecimal amount) {
        if (a.getCurrentBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient savings balance. Savings cannot go negative.");
        }
    }

    private SavingTransaction record(SavingAccount a, String type, BigDecimal amount, LocalDate date, String description) {
        return txs.save(SavingTransaction.builder()
                .account(a).transactionType(type).amount(amount)
                .transactionDate(date == null ? LocalDate.now() : date)
                .description(description).build());
    }

    private SavingAccount owned(User user, Long id) {
        return accounts.findById(id)
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Savings account not found."));
    }

    private SavingAccount lockOwned(User user, Long id) {
        return accounts.findByIdForUpdate(id)
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Savings account not found."));
    }

    static SavingAccountResponse toResponse(SavingAccount a) {
        return new SavingAccountResponse(a.getId(), a.getAccountName(), a.getCurrentBalance(), a.getTargetAmount(), a.getCreatedAt());
    }

    static SavingTransactionResponse toResponse(SavingTransaction t) {
        return new SavingTransactionResponse(t.getId(), t.getAccount().getId(), t.getAccount().getAccountName(),
                t.getTransactionType(), t.getAmount(), t.getTransactionDate(), t.getDescription());
    }
}
