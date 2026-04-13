package com.wageclock.wageclock.domain.payment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class MockPaymentService implements PaymentService{


    public PaymentResult processPayment (BigDecimal requestAmount){
        return PaymentResult.SUCCESS;
    }
}
