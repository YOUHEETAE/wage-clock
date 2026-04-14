package com.wageclock.wageclock.domain.payment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "payment_id")
    private Payment payment;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    @Column(nullable = false)
    private Payment.PaymentStatus status;

    @Builder
    public PaymentHistory(Payment payment, BigDecimal amount, Payment.PaymentStatus status) {
        this.payment = payment;
        this.amount = amount;
        this.changedAt = LocalDateTime.now();
        this.status = status;
    }
}
