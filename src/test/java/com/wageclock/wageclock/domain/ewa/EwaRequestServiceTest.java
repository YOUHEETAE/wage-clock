package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.EwaTransfer.EwaTransferService;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class EwaRequestServiceTest {
    @Mock EwaRequestRepository ewaRequestRepository;
    @Mock RedissonClient redissonClient;
    @Mock RLock lock;
    @Mock EwaRequest ewaRequest;
    @Mock EwaRequestProcessor ewaRequestProcessor;
    @Mock EwaTransferService ewaTransferService;
    @Mock PayPeriod payPeriod;
    @Mock PayPeriodRepository payPeriodRepository;

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
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(100));
        when(ewaTransferService.processTransfer(ewaRequest)).thenReturn(EwaRequest.EwaRequestStatus.PENDING);

        InitiateEwaResponse response = ewaRequestService.initiateEwa(1L, 1L);

        assertEquals(1L, response.ewaRequestId());
        assertEquals(BigDecimal.valueOf(100), response.amount());
        assertEquals(EwaRequest.EwaRequestStatus.PENDING, response.status());
    }


    @Test
    void 정상_거절() {
        when(ewaRequestProcessor.validateAndLockEwa(1L, 1L)).thenReturn(ewaRequest);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(100));
        when(ewaRequest.getStatus()).thenReturn(EwaRequest.EwaRequestStatus.REJECTED);

        EwaResponseDto response = ewaRequestService.rejectEwa(1L, 1L);
        assertEquals(EwaRequest.EwaRequestStatus.REJECTED, response.status());
    }
}
