package com.wageclock.wageclock.domain.ewarequest;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransferService;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class EwaRequestServiceTest {
    @Mock EwaRequestRepository ewaRequestRepository;
    @Mock EwaRequest ewaRequest;
    @Mock EwaRequestProcessor ewaRequestProcessor;
    @Mock EwaTransferService ewaTransferService;
    @Mock PayPeriod payPeriod;
    @Mock PayPeriodRepository payPeriodRepository;

    @InjectMocks
    EwaRequestService ewaRequestService;

    @Test
    void 정상_승인() {
        when(ewaRequestProcessor.validateAndMarkProcessing(1L, 1L)).thenReturn(ewaRequest);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(100));
        when(ewaTransferService.processTransfer(1L)).thenReturn(EwaRequest.EwaRequestStatus.APPROVED);

        InitiateEwaResponse response = ewaRequestService.initiateEwa(1L, 1L);

        assertEquals(1L, response.ewaRequestId());
        assertEquals(BigDecimal.valueOf(100), response.amount());
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, response.status());
    }

    @Test
    void 정상_거절() {
        EwaResponseDto responseDto = new EwaResponseDto(1L, BigDecimal.valueOf(100), EwaRequest.EwaRequestStatus.REJECTED);
        when(ewaRequestProcessor.validateAndRejectEwa(1L, 1L)).thenReturn(responseDto);

        EwaResponseDto response = ewaRequestService.rejectEwa(1L, 1L);

        assertEquals(EwaRequest.EwaRequestStatus.REJECTED, response.status());
    }
}
