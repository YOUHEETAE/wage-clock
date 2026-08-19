package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferProcessor;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRetryContext;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRepository;
import com.wageclock.wageclock.domain.port.TransferAccount;
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

    private static final TransferAccount REGISTERED_ACCOUNT =
            new TransferAccount("004", "1234-5678", "박사원");


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

    EwaTransferRetryContext retryContext(EwaTransfer.EwaTransferStatus status) {
        return new EwaTransferRetryContext(1L, status, BigDecimal.valueOf(50000), "TX-001", REGISTERED_ACCOUNT);
    }

    @Test
    void processEvent_재이체_성공_applyResult_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        WageTransferResult result = new WageTransferResult("TX-002", null, null);
        when(ewaTransferProcessor.loadRetryContext(1L)).thenReturn(retryContext(EwaTransfer.EwaTransferStatus.RETRYING));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-002"))).thenReturn(result);

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferProcessor).assignMessageNo(1L, "MSG-002");
        verify(ewaTransferFailureOutBoxProcessor).applyResult(result, 1L, event);
        verify(ewaTransferFailureOutBoxProcessor, never()).handleRetryOrFail(any(), any());
    }

    @Test
    void processEvent_재이체_VTIM_applyResult_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        WageTransferResult result = new WageTransferResult(null, "MSG-002", null);
        when(ewaTransferProcessor.loadRetryContext(1L)).thenReturn(retryContext(EwaTransfer.EwaTransferStatus.RETRYING));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-002"))).thenReturn(result);

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferFailureOutBoxProcessor).applyResult(result, 1L, event);
    }

    @Test
    void processEvent_재이체_확정실패_applyResult_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        WageTransferResult result = new WageTransferResult(null, null, "계좌 없음");
        when(ewaTransferProcessor.loadRetryContext(1L)).thenReturn(retryContext(EwaTransfer.EwaTransferStatus.RETRYING));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-002"))).thenReturn(result);

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferFailureOutBoxProcessor).applyResult(result, 1L, event);
    }

    @Test
    void processEvent_이체_예외발생_handleRetryOrFail_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        when(ewaTransferProcessor.loadRetryContext(1L)).thenReturn(retryContext(EwaTransfer.EwaTransferStatus.RETRYING));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-002");
        when(wageTransferPort.transfer(any(), any(), any())).thenThrow(new RuntimeException("네트워크 오류"));

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferFailureOutBoxProcessor).handleRetryOrFail(event, 1L);
        verify(ewaTransferFailureOutBoxProcessor, never()).applyResult(any(), any(), any());
    }

    @Test
    void processEvent_messageNo발급실패_handlePrepareRetryOrFail_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        when(ewaTransferProcessor.loadRetryContext(1L)).thenReturn(retryContext(EwaTransfer.EwaTransferStatus.RETRYING));
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenThrow(new RuntimeException("Redis 장애"));

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(ewaTransferFailureOutBoxProcessor).handlePrepareRetryOrFail(event, 1L);
        verify(wageTransferPort, never()).transfer(any(), any(), any());
        verify(ewaTransferFailureOutBoxProcessor, never()).handleRetryOrFail(any(), any());
    }

    @Test
    void processEvent_COMPLETED상태_새이체없이_이벤트종료() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        when(ewaTransferProcessor.loadRetryContext(1L)).thenReturn(retryContext(EwaTransfer.EwaTransferStatus.COMPLETED));

        ewaTransferFailureOutBoxService.processEvent(event);

        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PROCESSED, event.getStatus());
        verify(ewaTransferFailureOutBoxRepository).save(event);
        verify(wageTransferPort, never()).transfer(any(), any(), any());
        verify(wageTransferPort, never()).inquireTransfer(any());
    }

    @Test
    void processEvent_FAILED상태_새이체없이_이벤트종료() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        when(ewaTransferProcessor.loadRetryContext(1L)).thenReturn(retryContext(EwaTransfer.EwaTransferStatus.FAILED));

        ewaTransferFailureOutBoxService.processEvent(event);

        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PROCESSED, event.getStatus());
        verify(ewaTransferFailureOutBoxRepository).save(event);
        verify(wageTransferPort, never()).transfer(any(), any(), any());
        verify(wageTransferPort, never()).inquireTransfer(any());
    }

    @Test
    void processEvent_PENDING_INQUIRY상태_inquireTransfer_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        WageTransferResult result = new WageTransferResult("TX-002", null, null);
        when(ewaTransferProcessor.loadRetryContext(1L)).thenReturn(retryContext(EwaTransfer.EwaTransferStatus.PENDING_INQUIRY));
        when(wageTransferPort.inquireTransfer("TX-001")).thenReturn(result);

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(wageTransferPort, never()).prepareTransfer(any());
        verify(wageTransferPort).inquireTransfer("TX-001");
        verify(ewaTransferFailureOutBoxProcessor).applyResult(result, 1L, event);
    }

    @Test
    void processEvent_UNKNOWN상태_inquireTransfer_호출() {
        EwaTransferFailureOutBoxEvent event = buildEvent();
        WageTransferResult result = new WageTransferResult("TX-002", null, null);
        when(ewaTransferProcessor.loadRetryContext(1L)).thenReturn(retryContext(EwaTransfer.EwaTransferStatus.UNKNOWN));
        when(wageTransferPort.inquireTransfer("TX-001")).thenReturn(result);

        ewaTransferFailureOutBoxService.processEvent(event);

        verify(wageTransferPort, never()).prepareTransfer(any());
        verify(wageTransferPort).inquireTransfer("TX-001");
        verify(ewaTransferFailureOutBoxProcessor).applyResult(result, 1L, event);
    }
}
