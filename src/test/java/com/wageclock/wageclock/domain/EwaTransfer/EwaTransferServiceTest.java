package com.wageclock.wageclock.domain.EwaTransfer;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EwaTransferServiceTest {

    @Mock WageTransferPort wageTransferPort;
    @Mock EwaTransferProcessor ewaTransferProcessor;
    @InjectMocks EwaTransferService ewaTransferService;

    EwaTransfer buildMockTransfer() {
        Worker worker = mock(Worker.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getWorker()).thenReturn(worker);
        when(ewaTransfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        return ewaTransfer;
    }

    @Test
    void processTransfer_이체성공_assignTransferId_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = buildMockTransfer();
        when(ewaTransferProcessor.createEwaTransfer(ewaRequest)).thenReturn(ewaTransfer);
        when(wageTransferPort.transfer(any(), any(), eq("EWA-1")))
                .thenReturn(new WageTransferResult("TX-001", null, null, null));

        ewaTransferService.processTransfer(ewaRequest);

        verify(ewaTransferProcessor).assignTransferId("TX-001", 1L);
        verify(ewaTransferProcessor, never()).markFailed(any());
        verify(ewaTransferProcessor, never()).markUnknown(any());
    }

    @Test
    void processTransfer_VTIM_markPendingInquiry_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = buildMockTransfer();
        when(ewaTransferProcessor.createEwaTransfer(ewaRequest)).thenReturn(ewaTransfer);
        when(wageTransferPort.transfer(any(), any(), eq("EWA-1")))
                .thenReturn(new WageTransferResult(null, "MSG-001", null, null));

        ewaTransferService.processTransfer(ewaRequest);

        verify(ewaTransferProcessor).markPendingInquiry("MSG-001", 1L);
        verify(ewaTransferProcessor, never()).assignTransferId(any(), any());
    }

    @Test
    void processTransfer_확정실패_markFailed_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = buildMockTransfer();
        when(ewaTransferProcessor.createEwaTransfer(ewaRequest)).thenReturn(ewaTransfer);
        when(wageTransferPort.transfer(any(), any(), eq("EWA-1")))
                .thenReturn(new WageTransferResult(null, null, null, "계좌 없음"));

        ewaTransferService.processTransfer(ewaRequest);

        verify(ewaTransferProcessor).markFailed(1L);
        verify(ewaTransferProcessor, never()).assignTransferId(any(), any());
    }

    @Test
    void processTransfer_예외발생_markUnknown_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = buildMockTransfer();
        when(ewaTransferProcessor.createEwaTransfer(ewaRequest)).thenReturn(ewaTransfer);
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        ewaTransferService.processTransfer(ewaRequest);

        verify(ewaTransferProcessor).markUnknown(1L);
        verify(ewaTransferProcessor, never()).assignTransferId(any(), any());
    }

    @Test
    void inquiryTransfer_조회성공_assignTransferId_호출() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getPendingMessageNo()).thenReturn("MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult("TX-001", null, null, null));

        ewaTransferService.inquiryTransfer(ewaTransfer);

        verify(ewaTransferProcessor).assignTransferId("TX-001", 1L);
    }

    @Test
    void inquiryTransfer_VTIM_markPendingInquiry_갱신() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getPendingMessageNo()).thenReturn("MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult(null, "MSG-002", null, null));

        ewaTransferService.inquiryTransfer(ewaTransfer);

        verify(ewaTransferProcessor).markPendingInquiry("MSG-002", 1L);
    }

    @Test
    void inquiryTransfer_확정실패_markFailed_호출() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getPendingMessageNo()).thenReturn("MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult(null, null, null, "이체 실패"));

        ewaTransferService.inquiryTransfer(ewaTransfer);

        verify(ewaTransferProcessor).markFailed(1L);
    }

    @Test
    void inquiryTransfer_예외발생_markUnknown_호출() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getPendingMessageNo()).thenReturn("MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenThrow(new RuntimeException("타임아웃"));

        ewaTransferService.inquiryTransfer(ewaTransfer);

        verify(ewaTransferProcessor).markUnknown(1L);
    }

    @Test
    void receiveInterBankFailure_processor_위임() {
        ewaTransferService.receiveInterBankFailure("TX-001");
        verify(ewaTransferProcessor).receiveInterBankFailure("TX-001");
    }
}
