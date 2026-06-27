package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferProcessor;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EwaTransferFailureOutBoxProcessorTest {

    @Mock EwaTransferProcessor ewaTransferProcessor;
    @Mock EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    @InjectMocks EwaTransferFailureOutBoxProcessor ewaTransferFailureOutBoxProcessor;

    EwaTransferFailureOutBoxEvent buildEvent() {
        return EwaTransferFailureOutBoxEvent.builder()
                .ewaTransferId(1L)
                .messageNo("TX-001")
                .amount(BigDecimal.valueOf(50000))
                .build();
    }

    EwaTransfer buildMockTransfer() {
        EwaTransfer transfer = mock(EwaTransfer.class);
        when(transfer.getId()).thenReturn(1L);
        return transfer;
    }

    @Test
    void applyResult_성공_completeRetry_이벤트_PROCESSED_저장() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransfer();

        ewaTransferFailureOutBoxProcessor.applyResult(new WageTransferResult("TX-002", null, null), transfer, event);

        verify(ewaTransferProcessor).completeRetry(1L);
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PROCESSED, event.getStatus());
        verify(ewaTransferFailureOutBoxRepository).save(event);
    }

    @Test
    void applyResult_VTIM_markPendingInquiry_이벤트_PROCESSED_저장() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransfer();

        ewaTransferFailureOutBoxProcessor.applyResult(new WageTransferResult(null, "TX-002", null), transfer, event);

        verify(ewaTransferProcessor).markPendingInquiry(1L);
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PROCESSED, event.getStatus());
        verify(ewaTransferFailureOutBoxRepository).save(event);
    }

    @Test
    void applyResult_확정실패_failRetry_이벤트_FAILED_저장() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransfer();

        ewaTransferFailureOutBoxProcessor.applyResult(new WageTransferResult(null, null, "계좌 없음"), transfer, event);

        verify(ewaTransferProcessor).failRetry(1L);
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.FAILED, event.getStatus());
        verify(ewaTransferFailureOutBoxRepository).save(event);
    }

    @Test
    void applyResult_UNKNOWN_retryCount증가_unKnownRetry_저장() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransfer();

        ewaTransferFailureOutBoxProcessor.applyResult(new WageTransferResult(null, null, null), transfer, event);

        assertEquals(1, event.getRetryCount());
        verify(ewaTransferProcessor).unKnownRetry(1L);
        verify(ewaTransferFailureOutBoxRepository).save(event);
    }

    @Test
    void handleRetryOrFail_MAX_RETRY_미만_unKnownRetry_retryCount증가() {
        EwaTransferFailureOutBoxEvent event = buildEvent();

        ewaTransferFailureOutBoxProcessor.handleRetryOrFail(event, 1L);

        assertEquals(1, event.getRetryCount());
        verify(ewaTransferProcessor).unKnownRetry(1L);
        verify(ewaTransferProcessor, never()).failRetry(any());
        verify(ewaTransferFailureOutBoxRepository).save(event);
    }

    @Test
    void handleRetryOrFail_MAX_RETRY_도달_failRetry_이벤트_FAILED() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        for (int i = 0; i < EwaTransferFailureOutBoxEvent.MAX_RETRY - 1; i++) {
            event.incrementRetryCount();
        }

        ewaTransferFailureOutBoxProcessor.handleRetryOrFail(event, 1L);

        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.FAILED, event.getStatus());
        verify(ewaTransferProcessor).failRetry(1L);
        verify(ewaTransferProcessor, never()).unKnownRetry(any());
        verify(ewaTransferFailureOutBoxRepository).save(event);
    }

    @Test
    void handlePrepareRetryOrFail_MAX_RETRY_미만_상태_PENDING_유지_저장() {
        EwaTransferFailureOutBoxEvent event = buildEvent();

        ewaTransferFailureOutBoxProcessor.handlePrepareRetryOrFail(event, 1L);

        assertEquals(1, event.getRetryCount());
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PENDING, event.getStatus());
        verify(ewaTransferProcessor, never()).failRetry(any());
        verify(ewaTransferFailureOutBoxRepository).save(event);
    }

    @Test
    void handlePrepareRetryOrFail_MAX_RETRY_도달_failRetry_저장() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        for (int i = 0; i < EwaTransferFailureOutBoxEvent.MAX_RETRY - 1; i++) {
            event.incrementRetryCount();
        }

        ewaTransferFailureOutBoxProcessor.handlePrepareRetryOrFail(event, 1L);

        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.FAILED, event.getStatus());
        verify(ewaTransferProcessor).failRetry(1L);
        verify(ewaTransferFailureOutBoxRepository).save(event);
    }
}
