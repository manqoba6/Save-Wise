package com.savewise.repository;

import com.savewise.entity.SavingTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingTransactionRepository extends JpaRepository<SavingTransaction, Long> {

    @Query("select t from SavingTransaction t where t.account.user.id = :uid")
    Page<SavingTransaction> findAllForUser(@Param("uid") Long userId, Pageable pageable);

    @Query("select t from SavingTransaction t where t.account.user.id = :uid and t.account.id = :aid")
    Page<SavingTransaction> findForAccount(@Param("uid") Long userId, @Param("aid") Long accountId, Pageable pageable);

    @Query("select count(t) from SavingTransaction t where t.account.user.id = :uid "
         + "and t.transactionType = 'DEPOSIT' and t.transactionDate between :from and :to")
    long countDeposits(@Param("uid") Long userId, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    @Query("select coalesce(sum(t.amount), 0) from SavingTransaction t where t.account.user.id = :uid "
         + "and t.transactionType = :type and t.transactionDate between :from and :to")
    java.math.BigDecimal sumByType(@Param("uid") Long userId, @Param("type") String type,
                                   @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}
