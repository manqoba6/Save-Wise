package com.savewise.service;

import com.savewise.dto.IncomeRequest;
import com.savewise.dto.IncomeResponse;
import com.savewise.dto.PageResponse;
import com.savewise.entity.Income;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.IncomeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class IncomeService {
    private final IncomeRepository repo;
    private final CategoryService categories;

    public IncomeService(IncomeRepository repo, CategoryService categories) {
        this.repo = repo;
        this.categories = categories;
    }

    @Transactional
    public IncomeResponse create(User user, IncomeRequest req) {
        Income i = Income.builder()
                .user(user)
                .category(categories.requireOfType(req.categoryId(), "INCOME"))
                .amount(req.amount())
                .description(req.description())
                .incomeDate(req.incomeDate())
                .build();
        return toResponse(repo.save(i));
    }

    @Transactional(readOnly = true)
    public IncomeResponse get(User user, Long id) {
        return toResponse(owned(user, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<IncomeResponse> search(User user, Long categoryId, LocalDate from, LocalDate to,
                                               String q, int page, int size) {
        Specification<Income> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("user").get("id"), user.getId()));
            if (categoryId != null) p.add(cb.equal(root.get("category").get("id"), categoryId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("incomeDate"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("incomeDate"), to));
            if (q != null && !q.isBlank()) {
                p.add(cb.like(cb.lower(root.get("description")), "%" + q.trim().toLowerCase() + "%"));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "incomeDate", "id"));
        return PageResponse.from(repo.findAll(spec, pageable).map(IncomeService::toResponse));
    }

    @Transactional
    public IncomeResponse update(User user, Long id, IncomeRequest req) {
        Income i = owned(user, id);
        i.setCategory(categories.requireOfType(req.categoryId(), "INCOME"));
        i.setAmount(req.amount());
        i.setDescription(req.description());
        i.setIncomeDate(req.incomeDate());
        return toResponse(i);
    }

    @Transactional
    public void delete(User user, Long id) {
        repo.delete(owned(user, id));
    }

    private Income owned(User user, Long id) {
        return repo.findById(id)
                .filter(i -> i.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Income record not found."));
    }

    static IncomeResponse toResponse(Income i) {
        return new IncomeResponse(i.getId(), i.getCategory().getId(), i.getCategory().getName(),
                i.getAmount(), i.getDescription(), i.getIncomeDate());
    }
}
