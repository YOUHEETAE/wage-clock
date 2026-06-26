package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
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
    void processTransfer_이체성공_completed_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = buildMockTransfer();
        when(ewaTransferProcessor.createEwaTransfer(ewaRequest)).thenReturn(ewaTransfer);
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-001")))
                .thenReturn(new WageTransferResult("MSG-001", null, null));

        ewaTransferService.processTransfer(ewaRequest);

        verify(ewaTransferProcessor).assignMessageNo(1L, "MSG-001");
        verify(ewaTransferProcessor).completeTransfer(1L);
        verify(ewaTransferProcessor, never()).failTransfer(any());
        verify(ewaTransferProcessor, never()).unknownTransfer(any());
    }

    @Test
    void processTransfer_VTIM_markPendingInquiry_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = buildMockTransfer();
        when(ewaTransferProcessor.createEwaTransfer(ewaRequest)).thenReturn(ewaTransfer);
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-001")))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        ewaTransferService.processTransfer(ewaRequest);

        verify(ewaTransferProcessor).markPendingInquiry(1L);
        verify(ewaTransferProcessor, never()).completeTransfer(any());
    }

    @Test
    void processTransfer_확정실패_failed_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = buildMockTransfer();
        when(ewaTransferProcessor.createEwaTransfer(ewaRequest)).thenReturn(ewaTransfer);
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-001")))
                .thenReturn(new WageTransferResult(null, null, "계좌 없음"));

        ewaTransferService.processTransfer(ewaRequest);

        verify(ewaTransferProcessor).failTransfer(1L);
        verify(ewaTransferProcessor, never()).completeTransfer(any());
    }

    @Test
    void processTransfer_예외발생_unknown_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = buildMockTransfer();
        when(ewaTransferProcessor.createEwaTransfer(ewaRequest)).thenReturn(ewaTransfer);
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        ewaTransferService.processTransfer(ewaRequest);

        verify(ewaTransferProcessor).unknownTransfer(1L);
        verify(ewaTransferProcessor, never()).completeTransfer(any());
    }

    @Test
    void inquiryTransfer_조회성공_completed_호출() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getMessageNo()).thenReturn("MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult("MSG-001", null, null));

        ewaTransferService.inquiryTransfer(ewaTransfer);

        verify(ewaTransferProcessor).completeTransfer(1L);
    }

    @Test
    void inquiryTransfer_VTIM_markPendingInquiry_갱신() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getMessageNo()).thenReturn("MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        ewaTransferService.inquiryTransfer(ewaTransfer);

        verify(ewaTransferProcessor).markPendingInquiry(1L);
    }

    @Test
    void inquiryTransfer_확정실패_failed_호출() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getMessageNo()).thenReturn("MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult(null, null, "이체 실패"));

        ewaTransferService.inquiryTransfer(ewaTransfer);

        verify(ewaTransferProcessor).failTransfer(1L);
    }

    @Test
    void inquiryTransfer_예외발생_unknown_호출() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getMessageNo()).thenReturn("MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenThrow(new RuntimeException("타임아웃"));

        ewaTransferService.inquiryTransfer(ewaTransfer);

        verify(ewaTransferProcessor).unknownTransfer(1L);
    }

    @Test
    void processTransfer_messageNo발급실패_failTransfer_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransferProcessor.createEwaTransfer(ewaRequest)).thenReturn(ewaTransfer);
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenThrow(new RuntimeException("Redis 장애"));

        ewaTransferService.processTransfer(ewaRequest);

        verify(ewaTransferProcessor).failTransfer(1L);
        verify(ewaTransferProcessor, never()).unknownTransfer(any());
        verify(wageTransferPort, never()).transfer(any(), any(), any());
    }

    @Test
    void inquiryTransfer_모호한결과_unknown_호출() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getMessageNo()).thenReturn("MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult(null, null, null));

        ewaTransferService.inquiryTransfer(ewaTransfer);

        verify(ewaTransferProcessor).unknownTransfer(1L);
    }

    @Test
    void receiveInterBankFailure_processor_위임() {
        ewaTransferService.receiveInterBankFailure("TX-001");
        verify(ewaTransferProcessor).receiveInterBankFailure("TX-001");
    }
}