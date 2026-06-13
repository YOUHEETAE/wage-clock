package com.wageclock.wageclock.domain.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OutBoxSchedulerTest {
    @Mock
    EwaOutBoxEventRepository ewaOutBoxEventRepository;

    @Mock
    EwaOutBoxService ewaOutBoxService;

    @InjectMocks
    OutBoxScheduler outBoxScheduler;

    @Mock BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;

    @Mock BulkSettlementOutBoxService bulkSettlementOutBoxService;

    @Mock InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;

    @Mock InterBankFailureOutBoxEventService interBankFailureOutBoxEventService;

    @Test
    void processEwaOutBoxEvent_검증(){
        EwaOutBoxEvent event = EwaOutBoxEvent.builder()
                .portOnePaymentId("test-id")
                .ewaRequestId(1L)
                .amount(BigDecimal.valueOf(10000))
                .employerName("홍길동")
                .build();
        when(ewaOutBoxEventRepository.findByStatus(any())).thenReturn(List.of(event));
        outBoxScheduler.processEwaOutBoxEvent();
        verify(ewaOutBoxEventRepository).findByStatus(any());
        verify(ewaOutBoxService).processEvent(any());
    }

    @Test
    void processBulkSettlementOutBoxEvent_검증() {
        BulkSettlementOutBoxEvent event = BulkSettlementOutBoxEvent.builder()
                .bulkSettlementId(1L)
                .portOnePaymentId("BULK-001")
                .totalAmount(BigDecimal.valueOf(100000))
                .employerName("홍길동")
                .build();
        when(bulkSettlementOutBoxEventRepository.findByStatus(any())).thenReturn(List.of(event));
        outBoxScheduler.processBulkSettlementOutBoxEvent();
        verify(bulkSettlementOutBoxEventRepository).findByStatus(any());
        verify(bulkSettlementOutBoxService).processEvent(any());
    }

    @Test
    void processInterBankFailureOutBoxEvent_검증() {
        InterBankFailureOutBoxEvent event = InterBankFailureOutBoxEvent.builder()
                .transferId("TX-001")
                .portOnePaymentId("BULK-001")
                .bulkSettlementId(1L)
                .build();
        when(interBankFailureOutBoxEventRepository.findByStatus(any())).thenReturn(List.of(event));
        outBoxScheduler.processInterBankFailureOutBoxEvent();
        verify(interBankFailureOutBoxEventRepository).findByStatus(any());
        verify(interBankFailureOutBoxEventService).processEvent(any());
    }
}
