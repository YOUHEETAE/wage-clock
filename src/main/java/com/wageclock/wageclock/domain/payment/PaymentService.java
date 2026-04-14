package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.ewa.EwaRequest;


public interface PaymentService {
    Payment processPayment(EwaRequest  ewaRequest);
}
