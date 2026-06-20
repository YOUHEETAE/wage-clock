package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferProcessor;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRepository;
import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EwaTransferFailureOutBoxServiceTest {

    @Mock EwaTransferRepository ewaTransferRepository;
    @Mock WageTransferPort wageTransferPort;
    @Mock EwaTransferProcessor ewaTransferProcessor;
    @Mock EwaTransferFailureOutBoxResultHandler ewaTransferFailureOutBoxResultHandler;
    @Mock EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    @InjectMocks EwaTransferFailureOutBoxService ewaTransferFailureOutBoxService;

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
        when(transfer.getWorker()).thenReturn(mock(Worker.class));
        when(transfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        return transfer;
    }

    @Test
    void processEvent_재이체_성공_saveSuccess_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransfer();
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-002")))
                .thenReturn(new WageTransferResult("MSG-002", null, null));

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferProcessor).assignMessageNo(1L, "MSG-002");
        verify(ewaTransferFailureOutBoxResultHandler).saveSuccess(transfer, event);
        verify(ewaTransferProcessor, never()).failRetry(any());
        verify(ewaTransferProcessor, never()).unKnownRetry(any());
    }

    @Test
    void processEvent_재이체_VTIM_markPendingInquiry_이벤트_종료() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransfer();
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-002")))
                .thenReturn(new WageTransferResult(null, "MSG-002", null));

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferProcessor).markPendingInquiry(1L);
        // VTIM 시 이중 송금 방지: 이벤트를 PROCESSED로 종료
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PROCESSED, event.getStatus());
        verify(ewaTransferFailureOutBoxRepository).save(event);
    }

    @Test
    void processEvent_재이체_확정실패_retryFailed_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransfer();
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-002")))
                .thenReturn(new WageTransferResult(null, null, "계좌 없음"));

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferProcessor).failRetry(1L);
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.FAILED, event.getStatus());
        verify(ewaTransferFailureOutBoxRepository).save(event);
        verify(ewaTransferFailureOutBoxResultHandler, never()).saveSuccess(any(), any());
    }

    @Test
    void processEvent_예외발생_retryCount_증가_unKnownRetry_저장() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransfer();
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        ewaTransferFailureOutBoxService.processEvent(event);

        assertEquals(1, event.getRetryCount());
        verify(ewaTransferFailureOutBoxRepository).save(event);
        verify(ewaTransferProcessor).unKnownRetry(1L);
        verify(ewaTransferFailureOutBoxResultHandler, never()).saveSuccess(any(), any());
    }

    @Test
    void processEvent_MAX_RETRY_초과_이벤트_FAILED_상태() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        // MAX_RETRY - 1 번 선행 실패 (다음 호출로 MAX_RETRY 도달)
        for (int i = 0; i < EwaTransferFailureOutBoxEvent.MAX_RETRY - 1; i++) {
            event.incrementRetryCount();
        }
        EwaTransfer transfer = buildMockTransfer();
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        ewaTransferFailureOutBoxService.processEvent(event);

        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.FAILED, event.getStatus());
    }
}
