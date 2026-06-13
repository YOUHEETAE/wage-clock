package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.payment.Payment;
import com.wageclock.wageclock.domain.payment.PaymentRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EwaOutBoxResultHandlerTest {

    @Mock
    EwaOutBoxEventRepository ewaOutBoxEventRepository;

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    Employer employer;

    @Mock
    EwaRequest ewaRequest;

    @InjectMocks
    EwaOutBoxResultHandler ewaOutBoxResultHandler;

    @Test
    void saveSuccess_검증(){
        VirtualAccountResult account =
                new VirtualAccountResult("Toss", "1234-1234", "2026-05-24");
        Payment payment = Payment.builder()
                .portOnePaymentId("test-id")
                .employer(employer)
                .ewaRequest(ewaRequest)
                .amount(BigDecimal.valueOf(10000))
                .build();
        when(paymentRepository.findByPortOnePaymentId("test-id")).thenReturn(Optional.of(payment));
        EwaOutBoxEvent event = EwaOutBoxEvent.builder()
                .portOnePaymentId("test-id")
                .ewaRequestId(1L)
                .amount(BigDecimal.valueOf(10000))
                .employerName("홍길동")
                .build();
        ewaOutBoxResultHandler.saveSuccess(event,account);
        assertEquals("test-id", payment.getPortOnePaymentId());
        assertEquals("Toss", payment.getBank());
        assertEquals("1234-1234", payment.getAccountNumber());
        assertEquals("2026-05-24", payment.getExpiredAt());
        assertEquals(Payment.PaymentStatus.PROCESSING, payment.getStatus());
        assertEquals(EwaOutBoxEvent.OutBoxStatus.PROCESSED, event.getStatus());
    }
}
