package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItem;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItemRepository;
import com.wageclock.wageclock.domain.settlement.BulkSettlementProcessor;
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
    @Mock BulkSettlementProcessor bulkSettlementProcessor;

    @InjectMocks
    InterBankFailureOutBoxEventService interBankFailureOutBoxEventService;

    InterBankFailureOutBoxEvent buildEvent() {
        return InterBankFailureOutBoxEvent.builder()
                .messageNo("TX-001")
                .bulkSettlementItemId(10L)
                .portOnePaymentId("BULK-001")
                .bulkSettlementId(1L)
                .build();
    }

    BulkSettlementItem buildItem(BulkSettlementItem.BulkSettlementItemStatus status) {
        BulkSettlementItem item = mock(BulkSettlementItem.class);
        lenient().when(item.getMessageNo()).thenReturn("TX-001");
        lenient().when(item.getStatus()).thenReturn(status);
        lenient().when(item.getWorkerId()).thenReturn(1L);
        lenient().when(item.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        lenient().when(item.getId()).thenReturn(10L);
        return item;
    }

    @Test
    void processEvent_첫시도_성공_saveSuccess() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem(BulkSettlementItem.BulkSettlementItemStatus.RETRYING);
        Worker worker = mock(Worker.class);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(worker));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-002");
        when(wageTransferPort.transfer(eq(worker), eq(BigDecimal.valueOf(50000)), eq("TX-002")))
                .thenReturn(new WageTransferResult("TX-002", null, null));

        interBankFailureOutBoxEventService.processEvent(event);

        verify(bulkSettlementProcessor).assignMessageNo(10L, "TX-002");
        verify(interBankFailureOutBoxEventResultHandler).saveSuccess(item, event);
    }

    @Test
    void processEvent_첫시도_VTIM_markPendingInquiry() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem(BulkSettlementItem.BulkSettlementItemStatus.RETRYING);
        Worker worker = mock(Worker.class);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(worker));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-002");
        when(wageTransferPort.transfer(eq(worker), eq(BigDecimal.valueOf(50000)), eq("TX-002")))
                .thenReturn(new WageTransferResult(null, "TX-002", null));

        interBankFailureOutBoxEventService.processEvent(event);

        verify(bulkSettlementProcessor).markPendingInquiry(10L);
        verify(interBankFailureOutBoxEventResultHandler, never()).saveSuccess(any(), any());
    }

    @Test
    void processEvent_PENDING_INQUIRY_상태에서_조회성공_saveSuccess() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem(BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(mock(Worker.class)));
        when(wageTransferPort.inquireTransfer("TX-001"))
                .thenReturn(new WageTransferResult("TX-002", null, null));

        interBankFailureOutBoxEventService.processEvent(event);

        verify(wageTransferPort, never()).prepareTransfer(any());
        verify(interBankFailureOutBoxEventResultHandler).saveSuccess(item, event);
    }

    @Test
    void processEvent_UNKNOWN_상태에서_조회성공_saveSuccess() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem(BulkSettlementItem.BulkSettlementItemStatus.UNKNOWN);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(mock(Worker.class)));
        when(wageTransferPort.inquireTransfer("TX-001"))
                .thenReturn(new WageTransferResult("TX-002", null, null));

        interBankFailureOutBoxEventService.processEvent(event);

        verify(wageTransferPort, never()).prepareTransfer(any());
        verify(interBankFailureOutBoxEventResultHandler).saveSuccess(item, event);
    }

    @Test
    void processEvent_확정실패_failItem_이벤트_FAILED() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem(BulkSettlementItem.BulkSettlementItemStatus.RETRYING);
        Worker worker = mock(Worker.class);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(worker));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-002");
        when(wageTransferPort.transfer(eq(worker), eq(BigDecimal.valueOf(50000)), eq("TX-002")))
                .thenReturn(new WageTransferResult(null, null, "계좌 없음"));

        interBankFailureOutBoxEventService.processEvent(event);

        verify(bulkSettlementProcessor).failItem(10L);
        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED, event.getStatus());
        verify(interBankFailureOutBoxEventRepository).save(event);
    }

    @Test
    void processEvent_예외발생_unknownItem_retryCount증가() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem(BulkSettlementItem.BulkSettlementItemStatus.RETRYING);
        Worker worker = mock(Worker.class);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(worker));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-002");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        interBankFailureOutBoxEventService.processEvent(event);

        assertEquals(1, event.getRetryCount());
        verify(bulkSettlementProcessor).unknownItem(10L);
        verify(interBankFailureOutBoxEventRepository).save(event);
        verify(interBankFailureOutBoxEventResultHandler, never()).saveSuccess(any(), any());
    }

    @Test
    void processEvent_모호한결과_unknownItem_retryCount증가() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItem(BulkSettlementItem.BulkSettlementItemStatus.RETRYING);
        Worker worker = mock(Worker.class);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(worker));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-002");
        when(wageTransferPort.transfer(eq(worker), eq(BigDecimal.valueOf(50000)), eq("TX-002")))
                .thenReturn(new WageTransferResult(null, null, null));

        interBankFailureOutBoxEventService.processEvent(event);

        assertEquals(1, event.getRetryCount());
        verify(bulkSettlementProcessor).unknownItem(10L);
        verify(interBankFailureOutBoxEventRepository).save(event);
        verify(interBankFailureOutBoxEventResultHandler, never()).saveSuccess(any(), any());
    }

    @Test
    void processEvent_MAX_RETRY_초과_failItem_이벤트_FAILED() {
        InterBankFailureOutBoxEvent event = buildEvent();
        for (int i = 0; i < 4; i++) {
            event.incrementRetryCount();
        }
        BulkSettlementItem item = buildItem(BulkSettlementItem.BulkSettlementItemStatus.RETRYING);
        Worker worker = mock(Worker.class);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(workerRepository.findById(1L)).thenReturn(Optional.of(worker));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-002");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        interBankFailureOutBoxEventService.processEvent(event);

        verify(bulkSettlementProcessor).failItem(10L);
        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED, event.getStatus());
    }
}
