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

    @InjectMocks
    OutBoxScheduler outBoxScheduler;

    @Mock BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;

    @Mock BulkSettlementOutBoxService bulkSettlementOutBoxService;

    @Mock InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;

    @Mock InterBankFailureOutBoxEventService interBankFailureOutBoxEventService;

    @Mock EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;

    @Mock EwaTransferFailureOutBoxService ewaTransferFailureOutBoxService;


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
                .messageNo("TX-001")
                .bulkSettlementItemId(10L)
                .portOnePaymentId("BULK-001")
                .bulkSettlementId(1L)
                .build();
        when(interBankFailureOutBoxEventRepository.findByStatus(any())).thenReturn(List.of(event));
        outBoxScheduler.processInterBankFailureOutBoxEvent();
        verify(interBankFailureOutBoxEventRepository).findByStatus(any());
        verify(interBankFailureOutBoxEventService).processEvent(any());
    }

    @Test
    void processEwaTransferFailureOutBoxEvent_검증(){
        EwaTransferFailureOutBoxEvent event = EwaTransferFailureOutBoxEvent.builder()
                .ewaTransferId(1L)
                .messageNo("TX-001")
                .amount(BigDecimal.valueOf(100000))
                .build();
        when(ewaTransferFailureOutBoxRepository.findByStatus(any())).thenReturn(List.of(event));
        outBoxScheduler.processEwaTransferFailureOutBoxEvent();
        verify(ewaTransferFailureOutBoxRepository).findByStatus(any());
        verify(ewaTransferFailureOutBoxService).processEvent(any());
    }
}
