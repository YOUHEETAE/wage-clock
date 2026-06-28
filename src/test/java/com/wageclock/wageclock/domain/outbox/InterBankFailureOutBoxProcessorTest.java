package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItem;
import com.wageclock.wageclock.domain.settlement.BulkSettlementProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterBankFailureOutBoxProcessorTest {

    @Mock BulkSettlementProcessor bulkSettlementProcessor;
    @Mock InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    @InjectMocks InterBankFailureOutBoxProcessor interBankFailureOutBoxProcessor;

    InterBankFailureOutBoxEvent buildEvent() {
        return InterBankFailureOutBoxEvent.builder()
                .messageNo("TX-001")
                .bulkSettlementItemId(10L)
                .portOnePaymentId("BULK-001")
                .bulkSettlementId(1L)
                .build();
    }

    BulkSettlementItem buildMockItem() {
        BulkSettlementItem item = mock(BulkSettlementItem.class);
        when(item.getId()).thenReturn(10L);
        return item;
    }

    @Test
    void applyResult_성공_completeRetry_이벤트_PROCESSED_저장() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildMockItem();

        interBankFailureOutBoxProcessor.applyResult(new WageTransferResult("TX-002", null, null), event, item);

        verify(bulkSettlementProcessor).completeRetry(10L);
        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.PROCESSED, event.getStatus());
        verify(interBankFailureOutBoxEventRepository).save(event);
    }

    @Test
    void applyResult_VTIM_markPendingInquiry_이벤트_저장_없음() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildMockItem();

        interBankFailureOutBoxProcessor.applyResult(new WageTransferResult(null, "TX-002", null), event, item);

        verify(bulkSettlementProcessor).markPendingInquiry(10L);
        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.PENDING, event.getStatus());
        verify(interBankFailureOutBoxEventRepository, never()).save(any());
    }

    @Test
    void applyResult_확정실패_failItem_이벤트_FAILED_저장() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildMockItem();

        interBankFailureOutBoxProcessor.applyResult(new WageTransferResult(null, null, "계좌 없음"), event, item);

        verify(bulkSettlementProcessor).failItem(10L);
        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED, event.getStatus());
        verify(interBankFailureOutBoxEventRepository).save(event);
    }

    @Test
    void applyResult_UNKNOWN_retryCount증가_unknownItem_저장() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildMockItem();

        interBankFailureOutBoxProcessor.applyResult(new WageTransferResult(null, null, null), event, item);

        assertEquals(1, event.getRetryCount());
        verify(bulkSettlementProcessor).unknownItem(10L);
        verify(interBankFailureOutBoxEventRepository).save(event);
    }

    @Test
    void handleRetryOrFail_MAX_RETRY_미만_unknownItem_retryCount증가() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildMockItem();

        interBankFailureOutBoxProcessor.handleRetryOrFail(event, item);

        assertEquals(1, event.getRetryCount());
        verify(bulkSettlementProcessor).unknownItem(10L);
        verify(bulkSettlementProcessor, never()).failItem(any());
        verify(interBankFailureOutBoxEventRepository).save(event);
    }

    @Test
    void handleRetryOrFail_MAX_RETRY_도달_failItem_이벤트_FAILED() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildMockItem();
        for (int i = 0; i < 4; i++) {
            event.incrementRetryCount();
        }

        interBankFailureOutBoxProcessor.handleRetryOrFail(event, item);

        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED, event.getStatus());
        verify(bulkSettlementProcessor).failItem(10L);
        verify(bulkSettlementProcessor, never()).unknownItem(any());
        verify(interBankFailureOutBoxEventRepository).save(event);
    }

    @Test
    void handlePrepareRetryOrFail_MAX_RETRY_미만_상태_PENDING_유지_저장() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = mock(BulkSettlementItem.class);

        interBankFailureOutBoxProcessor.handlePrepareRetryOrFail(event, item);

        assertEquals(1, event.getRetryCount());
        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.PENDING, event.getStatus());
        verify(bulkSettlementProcessor, never()).failItem(any());
        verify(interBankFailureOutBoxEventRepository).save(event);
    }

    @Test
    void handlePrepareRetryOrFail_MAX_RETRY_도달_failItem_저장() {
        InterBankFailureOutBoxEvent event = buildEvent();
        BulkSettlementItem item = buildMockItem();
        for (int i = 0; i < 4; i++) {
            event.incrementRetryCount();
        }

        interBankFailureOutBoxProcessor.handlePrepareRetryOrFail(event, item);

        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED, event.getStatus());
        verify(bulkSettlementProcessor).failItem(10L);
        verify(interBankFailureOutBoxEventRepository).save(event);
    }
}