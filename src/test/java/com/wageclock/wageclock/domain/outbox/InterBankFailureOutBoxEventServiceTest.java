package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItem;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItemRepository;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterBankFailureOutBoxEventServiceTest {

    @Mock WageTransferPort wageTransferPort;
    @Mock BulkSettlementItemRepository bulkSettlementItemRepository;
    @Mock WorkerRepository workerRepository;
    @Mock InterBankFailureOutBoxEventResultHandler interBankFailureOutBoxEventResultHandler;
    @Mock InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;

    @InjectMocks
    InterBankFailureOutBoxEventService interBankFailureOutBoxEventService;

    InterBankFailureOutBoxEvent buildEvent() {
        return InterBankFailureOutBoxEvent.builder()
                .transferId("TX-001")
                .portOnePaymentId("BULK-001")
                .bulkSettlementId(1L)
                .build();
    }

    BulkSettlementItem buildItem() {
        BulkSettlementItem item = mock(BulkSettlementItem.class);
        lenient().when(item.getTransferId()).thenReturn("TX-001");
        when(item.getWorkerId()).thenReturn(1L);
        when(item.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(item.getId()).thenReturn(10L);
        return item;
    }

    @Test
    void processEvent_이체성공_saveSuccess() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem();
        Worker worker = mock(Worker.class);
        when(bulkSettlementItemRepository.findByTransferId("TX-001")).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(worker));
        when(wageTransferPort.transfer(eq(worker), eq(BigDecimal.valueOf(50000)), eq("BULK-10")))
                .thenReturn(new WageTransferResult("TX-002", null, null, null));

        interBankFailureOutBoxEventService.processEvent(event);

        verify(interBankFailureOutBoxEventResultHandler).saveSuccess("TX-001", "TX-002", event);
    }

    @Test
    void processEvent_VTIM_조회성공_saveSuccess() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem();
        Worker worker = mock(Worker.class);
        when(bulkSettlementItemRepository.findByTransferId("TX-001")).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(worker));
        when(wageTransferPort.transfer(eq(worker), eq(BigDecimal.valueOf(50000)), eq("BULK-10")))
                .thenReturn(new WageTransferResult(null, "MSG-001", null, null));
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult("TX-002", null, null, null));

        interBankFailureOutBoxEventService.processEvent(event);

        verify(interBankFailureOutBoxEventResultHandler).saveSuccess("TX-001", "TX-002", event);
    }

    @Test
    void processEvent_VTIM_조회미완료_retryCount증가() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem();
        Worker worker = mock(Worker.class);
        when(bulkSettlementItemRepository.findByTransferId("TX-001")).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(worker));
        when(wageTransferPort.transfer(eq(worker), eq(BigDecimal.valueOf(50000)), eq("BULK-10")))
                .thenReturn(new WageTransferResult(null, "MSG-001", null, null));
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult(null, "MSG-002", null, null));

        interBankFailureOutBoxEventService.processEvent(event);

        assertEquals(1, event.getRetryCount());
        verify(interBankFailureOutBoxEventRepository).save(event);
        verify(interBankFailureOutBoxEventResultHandler, never()).saveSuccess(any(), any(), any());
    }

    @Test
    void processEvent_이체예외_retryCount증가() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem();
        Worker worker = mock(Worker.class);
        when(bulkSettlementItemRepository.findByTransferId("TX-001")).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(worker));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        interBankFailureOutBoxEventService.processEvent(event);

        assertEquals(1, event.getRetryCount());
        verify(interBankFailureOutBoxEventRepository).save(event);
        verify(interBankFailureOutBoxEventResultHandler, never()).saveSuccess(any(), any(), any());
    }
}
