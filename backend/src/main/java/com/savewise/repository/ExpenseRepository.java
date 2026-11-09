package com.savewise.repository;

import com.savewise.dto.CategorySpend;
import com.savewise.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    @Query("select coalesce(sum(e.amount), 0) from Expense e "
         + "where e.user.id = :uid and e.category.id = :cid and e.expenseDate between :from and :to")
    BigDecimal sumSpent(@Param("uid") Long userId, @Param("cid") Long categoryId,
                        @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.user.id = :uid")
    BigDecimal sumAll(@Param("uid") Long userId);

    @Query("select coalesce(sum(e.amount), 0) from Expense e "
         + "where e.user.id = :uid and e.expenseDate between :from and :to")
    BigDecimal sumBetween(@Param("uid") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select new com.savewise.dto.CategorySpend(e.category.name, sum(e.amount)) from Expense e "
         + "where e.user.id = :uid and e.expenseDate between :from and :to "
         + "group by e.category.name order by sum(e.amount) desc")
    List<CategorySpend> spendByCategory(@Param("uid") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Expense> findByUserIdOrderByExpenseDateDescIdDesc(Long userId, Pageable pageable);

    boolean existsByRecurringIdAndExpenseDate(Long recurringId, LocalDate date);

    Page<Expense> findByRecurringIdAndUserId(Long recurringId, Long userId, Pageable pageable);

    List<Expense> findByUserIdAndExpenseDateBetweenOrderByExpenseDateAscIdAsc(Long userId, LocalDate from, LocalDate to);
}
