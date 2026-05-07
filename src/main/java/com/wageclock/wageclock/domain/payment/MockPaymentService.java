package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MockPaymentService implements PaymentService{

    private final PaymentRepository paymentRepository;

    public MockPaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }


    public Payment processPayment (EwaRequest ewaRequest){
        Payment payment = Payment.builder()
                .employer(ewaRequest.getEmployer())
                .ewaRequest(ewaRequest)
                .amount(ewaRequest.getRequestedAmount())
                .build();
        paymentRepository.save(payment);
        payment.processing();
        paymentRepository.save(payment);
        payment.completed();
        paymentRepository.save(payment);
        return payment;
    }
}
