package com.savewise.repository;

import com.savewise.entity.SavingAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavingAccountRepository extends JpaRepository<SavingAccount, Long> {
    List<SavingAccount> findByUserIdOrderByIdAsc(Long userId);

    @Query("select coalesce(sum(a.currentBalance), 0) from SavingAccount a where a.user.id = :uid")
    java.math.BigDecimal sumBalances(@Param("uid") Long userId);

    /** Row lock so two simultaneous deposits/withdrawals cannot overwrite each other's balance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SavingAccount a where a.id = :id")
    Optional<SavingAccount> findByIdForUpdate(@Param("id") Long id);
}
