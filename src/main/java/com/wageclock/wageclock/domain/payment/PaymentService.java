package com.wageclock.wageclock.domain.payment;

import java.math.BigDecimal;

public interface PaymentService {
    PaymentResult processPayment(BigDecimal requestAmount);
}
