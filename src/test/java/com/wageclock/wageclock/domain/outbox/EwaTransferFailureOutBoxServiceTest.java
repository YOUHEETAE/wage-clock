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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EwaTransferFailureOutBoxServiceTest {

    @Mock EwaTransferRepository ewaTransferRepository;
    @Mock WageTransferPort wageTransferPort;
    @Mock EwaTransferProcessor ewaTransferProcessor;
    @Mock EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    @Mock EwaTransferFailureOutBoxProcessor ewaTransferFailureOutBoxProcessor;
    @InjectMocks EwaTransferFailureOutBoxService ewaTransferFailureOutBoxService;

    EwaTransferFailureOutBoxEvent buildEvent() {
        return EwaTransferFailureOutBoxEvent.builder()
                .ewaTransferId(1L)
                .messageNo("TX-001")
                .amount(BigDecimal.valueOf(50000))
                .build();
    }

    EwaTransfer buildMockTransferForRetry() {
        EwaTransfer transfer = mock(EwaTransfer.class);
        when(transfer.getId()).thenReturn(1L);
        when(transfer.getWorker()).thenReturn(mock(Worker.class));
        when(transfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        return transfer;
    }

    @Test
    void processEvent_재이체_성공_applyResult_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransferForRetry();
        WageTransferResult result = new WageTransferResult("TX-002", null, null);
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-002"))).thenReturn(result);

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferProcessor).assignMessageNo(1L, "MSG-002");
        verify(ewaTransferFailureOutBoxProcessor).applyResult(result, transfer, event);
        verify(ewaTransferFailureOutBoxProcessor, never()).handleRetryOrFail(any(), any());
    }

    @Test
    void processEvent_재이체_VTIM_applyResult_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransferForRetry();
        WageTransferResult result = new WageTransferResult(null, "MSG-002", null);
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-002"))).thenReturn(result);

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferFailureOutBoxProcessor).applyResult(result, transfer, event);
    }

    @Test
    void processEvent_재이체_확정실패_applyResult_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransferForRetry();
        WageTransferResult result = new WageTransferResult(null, null, "계좌 없음");
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-002"))).thenReturn(result);

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferFailureOutBoxProcessor).applyResult(result, transfer, event);
    }

    @Test
    void processEvent_이체_예외발생_handleRetryOrFail_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = buildMockTransferForRetry();
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), any())).thenThrow(new RuntimeException("네트워크 오류"));

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferFailureOutBoxProcessor).handleRetryOrFail(event, 1L);
        verify(ewaTransferFailureOutBoxProcessor, never()).applyResult(any(), any(), any());
    }

    @Test
    void processEvent_messageNo발급실패_handlePrepareRetryOrFail_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = mock(EwaTransfer.class);
        when(transfer.getId()).thenReturn(1L);
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenThrow(new RuntimeException("Redis 장애"));

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferFailureOutBoxProcessor).handlePrepareRetryOrFail(event, 1L);
        verify(wageTransferPort, never()).transfer(any(), any(), any());
        verify(ewaTransferFailureOutBoxProcessor, never()).handleRetryOrFail(any(), any());
    }

    @Test
    void processEvent_COMPLETED상태_새이체없이_이벤트종료() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = mock(EwaTransfer.class);
        when(transfer.getStatus()).thenReturn(EwaTransfer.EwaTransferStatus.COMPLETED);
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));

        ewaTransferFailureOutBoxService.processEvent(event);

        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PROCESSED, event.getStatus());
        verify(ewaTransferFailureOutBoxRepository).save(event);
        verify(wageTransferPort, never()).transfer(any(), any(), any());
        verify(wageTransferPort, never()).inquireTransfer(any());
    }

    @Test
    void processEvent_FAILED상태_새이체없이_이벤트종료() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = mock(EwaTransfer.class);
        when(transfer.getStatus()).thenReturn(EwaTransfer.EwaTransferStatus.FAILED);
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));

        ewaTransferFailureOutBoxService.processEvent(event);

        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PROCESSED, event.getStatus());
        verify(ewaTransferFailureOutBoxRepository).save(event);
        verify(wageTransferPort, never()).transfer(any(), any(), any());
        verify(wageTransferPort, never()).inquireTransfer(any());
    }

    @Test
    void processEvent_PENDING_INQUIRY상태_inquireTransfer_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = mock(EwaTransfer.class);
        when(transfer.getStatus()).thenReturn(EwaTransfer.EwaTransferStatus.PENDING_INQUIRY);
        when(transfer.getMessageNo()).thenReturn("TX-001");
        WageTransferResult result = new WageTransferResult("TX-002", null, null);
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.inquireTransfer("TX-001")).thenReturn(result);

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(wageTransferPort, never()).prepareTransfer(any());
        verify(wageTransferPort).inquireTransfer("TX-001");
        verify(ewaTransferFailureOutBoxProcessor).applyResult(result, transfer, event);
    }

    @Test
    void processEvent_UNKNOWN상태_inquireTransfer_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        EwaTransfer transfer = mock(EwaTransfer.class);
        when(transfer.getStatus()).thenReturn(EwaTransfer.EwaTransferStatus.UNKNOWN);
        when(transfer.getMessageNo()).thenReturn("TX-001");
        WageTransferResult result = new WageTransferResult("TX-002", null, null);
        when(ewaTransferRepository.findByIdWithWorker(1L)).thenReturn(Optional.of(transfer));
        when(wageTransferPort.inquireTransfer("TX-001")).thenReturn(result);

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(wageTransferPort, never()).prepareTransfer(any());
        verify(wageTransferPort).inquireTransfer("TX-001");
        verify(ewaTransferFailureOutBoxProcessor).applyResult(result, transfer, event);
    }
}
