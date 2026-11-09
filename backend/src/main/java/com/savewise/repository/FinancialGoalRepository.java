package com.savewise.repository;

import com.savewise.entity.FinancialGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, Long> {
    List<FinancialGoal> findByUserIdOrderByIdDesc(Long userId);
    List<FinancialGoal> findByUserIdAndStatusOrderByIdDesc(Long userId, String status);
}
