package com.savewise.repository;

import com.savewise.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    Optional<Budget> findByUserIdAndCategoryIdAndBudgetMonth(Long userId, Long categoryId, LocalDate month);
    List<Budget> findByUserIdAndBudgetMonthOrderByIdAsc(Long userId, LocalDate month);
}
