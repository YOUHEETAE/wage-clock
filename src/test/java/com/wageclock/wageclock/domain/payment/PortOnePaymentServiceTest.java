package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.outbox.EwaOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.EwaOutBoxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PortOnePaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    VirtualAccountPort virtualAccountPort;
    @InjectMocks
    PortOnePaymentService portOnePaymentService;
    @Mock
    Employer employer;
    @Mock
    EwaRequest ewaRequest;
    @Mock
    EwaOutBoxEventRepository ewaOutBoxEventRepository;
    @Mock
    EwaOutBoxEvent ewaOutBoxEvent;

    @Test
    void processPayment_검증() {
        when(ewaRequest.getEmployer()).thenReturn(employer);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(1000));
        when(paymentRepository.save(any()))
                .then(invocation -> invocation.getArgument(0));
        Payment payment = portOnePaymentService.processPayment(ewaRequest);
        assertEquals(BigDecimal.valueOf(1000), payment.getAmount());
        assertEquals(Payment.PaymentStatus.READY, payment.getStatus());
        assertEquals(1, payment.getHistories().size());
        assertEquals(Payment.PaymentStatus.READY, payment.getHistories().get(0).getStatus());
    }

    @Test
    void updatePayment_검증(){
        when(ewaOutBoxEventRepository.findByPortOnePaymentId(any())).thenReturn(Optional.of(ewaOutBoxEvent));
        VirtualAccountResult account = new VirtualAccountResult("Toss", "1234", "2026-05-05");
        Payment payment = portOnePaymentService.processPayment(ewaRequest);
        portOnePaymentService.updatePayment(payment, account);
        assertEquals("Toss", payment.getBank());
        assertEquals("1234", payment.getAccountNumber());
        assertEquals("2026-05-05",payment.getExpiredAt());
        assertEquals(Payment.PaymentStatus.PROCESSING, payment.getStatus());
        assertEquals(2, payment.getHistories().size());
        assertEquals(Payment.PaymentStatus.PROCESSING, payment.getHistories().get(1).getStatus());
    }
}
