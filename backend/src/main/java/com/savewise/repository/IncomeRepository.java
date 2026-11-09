package com.savewise.repository;

import com.savewise.entity.Income;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface IncomeRepository extends JpaRepository<Income, Long>, JpaSpecificationExecutor<Income> {

    @Query("select coalesce(sum(i.amount), 0) from Income i where i.user.id = :uid")
    BigDecimal sumAll(@Param("uid") Long userId);

    @Query("select coalesce(sum(i.amount), 0) from Income i "
         + "where i.user.id = :uid and i.incomeDate between :from and :to")
    BigDecimal sumBetween(@Param("uid") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Income> findByUserIdOrderByIncomeDateDescIdDesc(Long userId, Pageable pageable);

    boolean existsByRecurringIdAndIncomeDate(Long recurringId, LocalDate date);

    Page<Income> findByRecurringIdAndUserId(Long recurringId, Long userId, Pageable pageable);

    List<Income> findByUserIdAndIncomeDateBetweenOrderByIncomeDateAscIdAsc(Long userId, LocalDate from, LocalDate to);
}
