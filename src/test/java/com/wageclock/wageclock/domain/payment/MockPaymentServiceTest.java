package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.ewa.EwaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class MockPaymentServiceTest {
    @Mock
    EwaRequest ewaRequest;
    @Mock
    Employer employer;
    @Mock
    PaymentRepository  paymentRepository;

    @InjectMocks
    MockPaymentService mockPaymentService;

    @BeforeEach
    public void setup(){
        when(ewaRequest.getEmployer()).thenReturn(employer);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(2000));
    }

    @Test
    void 정상_결제_completed_반환(){
        Payment payment = mockPaymentService.processPayment(ewaRequest);
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals(3, payment.getHistories().size());
        assertEquals(Payment.PaymentStatus.READY, payment.getHistories().get(0).getStatus());
        assertEquals(Payment.PaymentStatus.PROCESSING, payment.getHistories().get(1).getStatus());
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.getHistories().get(2).getStatus());
    }
}
