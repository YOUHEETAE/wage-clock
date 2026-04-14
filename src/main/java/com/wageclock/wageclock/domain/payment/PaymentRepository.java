package com.wageclock.wageclock.domain.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByStatus(Payment.PaymentStatus status);
    @Query("SELECT p FROM Payment p JOIN FETCH p.histories")
    List<Payment> findAllWithHistories();
}
