package com.savewise.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecurringTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recurring_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** DAILY, WEEKLY, MONTHLY or YEARLY */
    @Column(nullable = false, length = 20)
    private String frequency;

    /** INCOME or EXPENSE */
    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Column(name = "next_execution", nullable = false)
    private LocalDate nextExecution;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private String description;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
