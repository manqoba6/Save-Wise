package com.savewise.repository;

import com.savewise.entity.RecurringTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {
    List<RecurringTransaction> findByUserIdOrderByIdDesc(Long userId);
    List<RecurringTransaction> findByUserIdAndActiveOrderByIdDesc(Long userId, boolean active);

    @Query("select r.id from RecurringTransaction r where r.active = true and r.nextExecution <= :today")
    List<Long> findDueIds(@Param("today") LocalDate today);

    /**
     * Locks the row; if another instance already holds it the row is skipped (empty result),
     * so two app instances never process the same schedule at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select r from RecurringTransaction r where r.id = :id and r.active = true and r.nextExecution <= :today")
    Optional<RecurringTransaction> lockDue(@Param("id") Long id, @Param("today") LocalDate today);
}
