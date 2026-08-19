package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.port.TransferAccount;
import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
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

    private static final TransferAccount REGISTERED_ACCOUNT =
            new TransferAccount("004", "1234-5678", "박사원");

    EwaTransferContext buildContext() {
        return new EwaTransferContext(1L, BigDecimal.valueOf(50000), REGISTERED_ACCOUNT);
    }

    @Test
    void processTransfer_이체성공_completed_호출() {
        when(ewaTransferProcessor.createEwaTransfer(1L)).thenReturn(buildContext());
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-001")))
                .thenReturn(new WageTransferResult("MSG-001", null, null));

        ewaTransferService.processTransfer(1L);

        verify(ewaTransferProcessor).assignMessageNo(1L, "MSG-001");
        verify(ewaTransferProcessor).completeTransfer(1L);
        verify(ewaTransferProcessor, never()).failTransfer(any());
        verify(ewaTransferProcessor, never()).unknownTransfer(any());
    }

    @Test
    void processTransfer_VTIM_markPendingInquiry_호출() {
        when(ewaTransferProcessor.createEwaTransfer(1L)).thenReturn(buildContext());
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-001")))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        ewaTransferService.processTransfer(1L);

        verify(ewaTransferProcessor).markPendingInquiry(1L);
        verify(ewaTransferProcessor, never()).completeTransfer(any());
    }

    @Test
    void processTransfer_확정실패_failed_호출() {
        when(ewaTransferProcessor.createEwaTransfer(1L)).thenReturn(buildContext());
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-001")))
                .thenReturn(new WageTransferResult(null, null, "계좌 없음"));

        ewaTransferService.processTransfer(1L);

        verify(ewaTransferProcessor).failTransfer(1L);
        verify(ewaTransferProcessor, never()).completeTransfer(any());
    }

    @Test
    void processTransfer_예외발생_unknown_호출() {
        when(ewaTransferProcessor.createEwaTransfer(1L)).thenReturn(buildContext());
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        ewaTransferService.processTransfer(1L);

        verify(ewaTransferProcessor).unknownTransfer(1L);
        verify(ewaTransferProcessor, never()).completeTransfer(any());
    }

    @Test
    void inquiryTransfer_조회성공_completed_호출() {
        EwaTransferInquiryContext context = new EwaTransferInquiryContext(1L, "MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult("MSG-001", null, null));

        ewaTransferService.inquiryTransfer(context);

        verify(ewaTransferProcessor).completeTransfer(1L);
    }

    @Test
    void inquiryTransfer_VTIM_markPendingInquiry_갱신() {
        EwaTransferInquiryContext context = new EwaTransferInquiryContext(1L, "MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        ewaTransferService.inquiryTransfer(context);

        verify(ewaTransferProcessor).markPendingInquiry(1L);
    }

    @Test
    void inquiryTransfer_확정실패_failed_호출() {
        EwaTransferInquiryContext context = new EwaTransferInquiryContext(1L, "MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult(null, null, "이체 실패"));

        ewaTransferService.inquiryTransfer(context);

        verify(ewaTransferProcessor).failTransfer(1L);
    }

    @Test
    void inquiryTransfer_예외발생_unknown_호출() {
        EwaTransferInquiryContext context = new EwaTransferInquiryContext(1L, "MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenThrow(new RuntimeException("타임아웃"));

        ewaTransferService.inquiryTransfer(context);

        verify(ewaTransferProcessor).unknownTransfer(1L);
    }

    // 보낼 수 없는 건에 전문번호를 발급하지 않는다. 돈이 안 나갔으므로 확정 실패로 한도를 되돌린다.
    @Test
    void processTransfer_계좌_미등록_전문번호_발급없이_failTransfer() {
        when(ewaTransferProcessor.createEwaTransfer(1L)).thenReturn(
                new EwaTransferContext(1L, BigDecimal.valueOf(50000),
                        new TransferAccount(null, null, null)));

        ewaTransferService.processTransfer(1L);

        verify(ewaTransferProcessor).failTransfer(1L);
        verify(wageTransferPort, never()).prepareTransfer(any());
        verify(wageTransferPort, never()).transfer(any(), any(), any());
    }

    // 어댑터가 실제로 읽는 값이 컨텍스트를 통해 그대로 전달되는지 — 엔티티를 넘기면 여기서 깨진다
    @Test
    void processTransfer_계좌정보가_어댑터로_전달된다() {
        when(ewaTransferProcessor.createEwaTransfer(1L)).thenReturn(buildContext());
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), eq("MSG-001")))
                .thenReturn(new WageTransferResult("MSG-001", null, null));

        ewaTransferService.processTransfer(1L);

        verify(wageTransferPort).transfer(eq(REGISTERED_ACCOUNT), eq(BigDecimal.valueOf(50000)), eq("MSG-001"));
    }

    @Test
    void processTransfer_messageNo발급실패_failTransfer_호출() {
        when(ewaTransferProcessor.createEwaTransfer(1L)).thenReturn(buildContext());
        when(wageTransferPort.prepareTransfer(TransferType.EWA)).thenThrow(new RuntimeException("Redis 장애"));

        ewaTransferService.processTransfer(1L);

        verify(ewaTransferProcessor).failTransfer(1L);
        verify(ewaTransferProcessor, never()).unknownTransfer(any());
        verify(wageTransferPort, never()).transfer(any(), any(), any());
    }

    @Test
    void inquiryTransfer_모호한결과_unknown_호출() {
        EwaTransferInquiryContext context = new EwaTransferInquiryContext(1L, "MSG-001");
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult(null, null, null));

        ewaTransferService.inquiryTransfer(context);

        verify(ewaTransferProcessor).unknownTransfer(1L);
    }

    @Test
    void receiveInterBankFailure_processor_위임() {
        ewaTransferService.receiveInterBankFailure("TX-001");
        verify(ewaTransferProcessor).receiveInterBankFailure("TX-001");
    }
}