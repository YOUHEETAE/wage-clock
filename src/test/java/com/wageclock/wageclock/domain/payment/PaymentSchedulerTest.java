package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.ewa.EwaRequestRepository;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class PaymentSchedulerTest {
    @Mock
    Payment payment;
    @Mock
    EwaRequest ewaRequest;
    @Mock
    WorkSession workSession;
    @Mock
    PaymentRepository  paymentRepository;
    @Mock
    WorkSessionRepository workSessionRepository;
    @Mock
    EwaRequestRepository ewaRequestRepository;

    @InjectMocks
    PaymentScheduler paymentScheduler;

    @BeforeEach
    public void setUp() {
        when(payment.getEwaRequest()).thenReturn(ewaRequest);
        when(payment.getWorkSession()).thenReturn(workSession);
        when(payment.getAmount()).thenReturn(BigDecimal.valueOf(1000));
        when(paymentRepository.findByStatus(Payment.PaymentStatus.UNKNOWN)).thenReturn(List.of(payment));
    }

    @Test
    void UNKNOWN_결제_FAILED_처리(){
        paymentScheduler.retryPayment();
        verify(ewaRequest).rejected();
        verify(payment).failed();
        verify(workSession).subtractEwaAmount(BigDecimal.valueOf(1000));
    }
}
