package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.payment.Payment;
import com.wageclock.wageclock.domain.payment.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PortOneWebhookServiceTest {
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    EwaRequest ewaRequest;
    @Mock
    Employer employer;
    @Mock
    EwaRequestRepository ewaRequestRepository;
    @InjectMocks
    PortOneWebhookService portOneWebhookService;

    @Test
    void approveEwa_검증(){
        Payment payment = Payment.builder()
                .portOnePaymentId("test-payment-id")
                .employer(employer)
                .ewaRequest(ewaRequest)
                .amount(BigDecimal.valueOf(100))
                .bank("SHINHAN")
                .accountNumber("123456")
                .expiredAt("2026-05-07")
                .build();

        when(paymentRepository.findByPortOnePaymentId("test-payment-id"))
                .thenReturn(Optional.of(payment));

        portOneWebhookService.approveEwa("test-payment-id");
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.getStatus());
        verify(ewaRequest).approved();
    }
}
