package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.ewa.EwaSettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class PaymentSchedulerTest {
    @Mock
    Payment payment;
    @Mock
    PaymentRepository  paymentRepository;
    @Mock
    VirtualAccountPort virtualAccountPort;
    @Mock
    EwaSettlementService ewaSettlementService;

    @InjectMocks
    PaymentScheduler paymentScheduler;

    @BeforeEach
    public void setUp() {
        when(paymentRepository.findByStatus(Payment.PaymentStatus.PROCESSING)).thenReturn(List.of(payment));
    }

    @Test
    void PROCESSING_결제_PAID_처리(){
        when(virtualAccountPort.getVirtualAccountStatus(any())).thenReturn("PAID");
        paymentScheduler.retryPayment();
        verify(ewaSettlementService).approveEwa(any());
    }
    @Test
    void PROCESSING_결제_FAILED_처리(){
        when(virtualAccountPort.getVirtualAccountStatus(any())).thenReturn("FAILED");
        paymentScheduler.retryPayment();
        verify(ewaSettlementService).failEwa(any());
    }
    @Test
    void PROCESSING_결제_CANCELLED_처리(){
        when(virtualAccountPort.getVirtualAccountStatus(any())).thenReturn("CANCELLED");
        paymentScheduler.retryPayment();
        verify(ewaSettlementService).failEwa(any());
    }
}
