package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItem;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItemRepository;
import com.wageclock.wageclock.domain.settlement.BulkSettlementProcessor;
import com.wageclock.wageclock.domain.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterBankFailureOutBoxEventServiceTest {

    @Mock WageTransferPort wageTransferPort;
    @Mock BulkSettlementItemRepository bulkSettlementItemRepository;
    @Mock BulkSettlementProcessor bulkSettlementProcessor;
    @Mock InterBankFailureOutBoxProcessor interBankFailureOutBoxProcessor;
    @InjectMocks InterBankFailureOutBoxEventService interBankFailureOutBoxEventService;

    InterBankFailureOutBoxEvent buildEvent() {
        return InterBankFailureOutBoxEvent.builder()
                .messageNo("TX-001")
                .bulkSettlementItemId(10L)
                .portOnePaymentId("BULK-001")
                .bulkSettlementId(1L)
                .build();
    }

    BulkSettlementItem buildItemForRetry() {
        BulkSettlementItem item = mock(BulkSettlementItem.class);
        when(item.getStatus()).thenReturn(BulkSettlementItem.BulkSettlementItemStatus.RETRYING);
        when(item.getId()).thenReturn(10L);
        when(item.getWorker()).thenReturn(mock(Worker.class));
        when(item.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        return item;
    }

    BulkSettlementItem buildItemForInquiry(BulkSettlementItem.BulkSettlementItemStatus status) {
        BulkSettlementItem item = mock(BulkSettlementItem.class);
        when(item.getStatus()).thenReturn(status);
        when(item.getMessageNo()).thenReturn("TX-001");
        return item;
    }

    @Test
    void processEvent_첫시도_성공_applyResult_호출() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItemForRetry();
        WageTransferResult result = new WageTransferResult("TX-002", null, null);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-002");
        when(wageTransferPort.transfer(any(), any(), eq("TX-002"))).thenReturn(result);

        interBankFailureOutBoxEventService.processEvent(event);

        verify(bulkSettlementProcessor).assignMessageNo(10L, "TX-002");
        verify(interBankFailureOutBoxProcessor).applyResult(result, event, item);
    }

    @Test
    void processEvent_첫시도_VTIM_applyResult_호출() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItemForRetry();
        WageTransferResult result = new WageTransferResult(null, "TX-002", null);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-002");
        when(wageTransferPort.transfer(any(), any(), eq("TX-002"))).thenReturn(result);

        interBankFailureOutBoxEventService.processEvent(event);

        verify(interBankFailureOutBoxProcessor).applyResult(result, event, item);
    }

    @Test
    void processEvent_PENDING_INQUIRY_상태에서_inquireTransfer_applyResult_호출() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItemForInquiry(BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY);
        WageTransferResult result = new WageTransferResult("TX-002", null, null);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(wageTransferPort.inquireTransfer("TX-001")).thenReturn(result);

        interBankFailureOutBoxEventService.processEvent(event);

        verify(wageTransferPort, never()).prepareTransfer(any());
        verify(wageTransferPort).inquireTransfer("TX-001");
        verify(interBankFailureOutBoxProcessor).applyResult(result, event, item);
    }

    @Test
    void processEvent_UNKNOWN_상태에서_inquireTransfer_applyResult_호출() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItemForInquiry(BulkSettlementItem.BulkSettlementItemStatus.UNKNOWN);
        WageTransferResult result = new WageTransferResult("TX-002", null, null);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(wageTransferPort.inquireTransfer("TX-001")).thenReturn(result);

        interBankFailureOutBoxEventService.processEvent(event);

        verify(wageTransferPort, never()).prepareTransfer(any());
        verify(wageTransferPort).inquireTransfer("TX-001");
        verify(interBankFailureOutBoxProcessor).applyResult(result, event, item);
    }

    @Test
    void processEvent_확정실패_applyResult_호출() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItemForRetry();
        WageTransferResult result = new WageTransferResult(null, null, "계좌 없음");
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-002");
        when(wageTransferPort.transfer(any(), any(), eq("TX-002"))).thenReturn(result);

        interBankFailureOutBoxEventService.processEvent(event);

        verify(interBankFailureOutBoxProcessor).applyResult(result, event, item);
    }

    @Test
    void processEvent_이체_예외발생_handleRetryOrFail_호출() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildItemForRetry();
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-002");
        when(wageTransferPort.transfer(any(), any(), any())).thenThrow(new RuntimeException("네트워크 오류"));

        interBankFailureOutBoxEventService.processEvent(event);

        verify(interBankFailureOutBoxProcessor).handleRetryOrFail(event, item);
        verify(interBankFailureOutBoxProcessor, never()).applyResult(any(), any(), any());
    }

    @Test
    void processEvent_messageNo발급실패_handlePrepareRetryOrFail_호출() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = mock(BulkSettlementItem.class);
        when(item.getStatus()).thenReturn(BulkSettlementItem.BulkSettlementItemStatus.RETRYING);
        when(bulkSettlementItemRepository.findByIdWithEmployment(10L)).thenReturn(Optional.of(item));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenThrow(new RuntimeException("Redis 장애"));

        interBankFailureOutBoxEventService.processEvent(event);

        verify(interBankFailureOutBoxProcessor).handlePrepareRetryOrFail(event, item);
        verify(wageTransferPort, never()).transfer(any(), any(), any());
        verify(interBankFailureOutBoxProcessor, never()).handleRetryOrFail(any(), any());
    }
}
