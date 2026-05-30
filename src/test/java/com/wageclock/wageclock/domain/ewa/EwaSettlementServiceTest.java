package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.payment.Payment;
import com.wageclock.wageclock.domain.payment.PaymentRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EwaSettlementServiceTest {
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    EwaRequest ewaRequest;
    @Mock
    Employer employer;
    @Mock
    EwaRequestRepository ewaRequestRepository;
    @InjectMocks
    EwaSettlementService ewaSettlementService;
    @Mock
    EwaTransactionRepository ewaTransactionRepository;
    @Mock
    EwaTransaction ewaTransaction;
    @Mock
    PayPeriod payPeriod;
    @Mock
    PayPeriodRepository payPeriodRepository;

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

        ewaSettlementService.approveEwa("test-payment-id");
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.getStatus());
        verify(ewaRequest).approved();
        ArgumentCaptor<EwaTransaction> captor = ArgumentCaptor.captor();
        verify(ewaTransactionRepository).save(captor.capture());
        EwaTransaction captured = captor.getValue();
        assertEquals(payment.getEwaRequest(), captured.getEwaRequest());
        assertEquals(BigDecimal.valueOf(100), captured.getAmount());
    }
    @Test
    void failEwa_검증(){
        Payment payment = Payment.builder()
                .portOnePaymentId("test-payment-id")
                .employer(employer)
                .ewaRequest(ewaRequest)
                .amount(BigDecimal.valueOf(100))
                .bank("SHINHAN")
                .accountNumber("123456")
                .expiredAt("2026-05-07")
                .build();
        when(ewaRequest.getPayPeriod()).thenReturn(payPeriod);
        when(paymentRepository.findByPortOnePaymentId("test-payment-id"))
                .thenReturn(Optional.of(payment));
        ewaSettlementService.failEwa("test-payment-id");
        assertEquals(Payment.PaymentStatus.FAILED, payment.getStatus());
        verify(payPeriod).subtractEwaAmount(BigDecimal.valueOf(100));
        verify(ewaRequest).failed();
    }
}
