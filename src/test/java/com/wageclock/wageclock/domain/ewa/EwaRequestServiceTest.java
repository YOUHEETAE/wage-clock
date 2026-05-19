package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.payment.Payment;
import com.wageclock.wageclock.domain.payment.PortOnePaymentService;
import com.wageclock.wageclock.domain.payment.VirtualAccountResult;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class EwaRequestServiceTest {
    @Mock EwaRequestRepository ewaRequestRepository;
    @Mock RedissonClient redissonClient;
    @Mock RLock lock;
    @Mock Employment employment;
    @Mock Worker worker;
    @Mock WorkSessionRepository workSessionRepository;
    @Mock WorkSession workSession;
    @Mock PortOnePaymentService portOnePaymentService;
    @Mock EwaRequest ewaRequest;
    @Mock Payment payment;
    @Mock EwaRequestProcessor ewaRequestProcessor;

    @InjectMocks
    EwaRequestService ewaRequestService;

    @Test
    void 락_점유_실패_시_예외() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.ONE, "key-1");
        assertThrows(RuntimeException.class, () -> ewaRequestService.requestEwa(ewaRequestDto, 1L));
    }

    @Test
    void 정상_승인() {
        when(ewaRequestProcessor.validateAndLockEwa(1L, 1L)).thenReturn(ewaRequest);
        when(ewaRequest.getStatus())
                .thenReturn(EwaRequest.EwaRequestStatus.PENDING);
        when(portOnePaymentService.processPayment(any())).thenReturn(payment);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(100));
        when(payment.getPortOnePaymentId()).thenReturn("test-id");
        when(portOnePaymentService.getAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("SHINHAN", "123456", "2026-05-07"));

        InitiateEwaResponse response = ewaRequestService.initiateEwa(1L, 1L);
        assertEquals(EwaRequest.EwaRequestStatus.PENDING, response.status());
    }

    @Test
    void 정상_거절() {
        when(ewaRequestRepository.findByIdWithLock(1L)).thenReturn(Optional.of(ewaRequest));
        when(ewaRequest.getStatus())
                .thenReturn(EwaRequest.EwaRequestStatus.PENDING)
                .thenReturn(EwaRequest.EwaRequestStatus.REJECTED);
        when(ewaRequest.getEmployerId()).thenReturn(1L);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(100));
        when(ewaRequest.getWorkSession()).thenReturn(workSession);

        EwaResponseDto response = ewaRequestService.rejectEwa(1L, 1L);
        assertEquals(EwaRequest.EwaRequestStatus.REJECTED, response.status());
    }
}
