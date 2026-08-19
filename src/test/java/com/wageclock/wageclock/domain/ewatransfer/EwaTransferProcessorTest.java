package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxRepository;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.port.TransferAccount;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EwaTransferProcessorTest {

    @Mock EwaTransferRepository ewaTransferRepository;
    @Mock EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    @Mock PayPeriodRepository payPeriodRepository;
    @Mock EwaRequestRepository ewaRequestRepository;
    @InjectMocks EwaTransferProcessor ewaTransferProcessor;

    /** 한도를 되돌리는 경로는 PayPeriod를 락으로 다시 조회한다. */
    private PayPeriod stubLockedPayPeriod(EwaRequest ewaRequest) {
        PayPeriod payPeriod = mock(PayPeriod.class);
        when(ewaRequest.getPayPeriodId()).thenReturn(1L);
        when(payPeriodRepository.findByIdWithLock(1L)).thenReturn(Optional.of(payPeriod));
        return payPeriod;
    }

    // 이체는 트랜잭션 밖에서 일어나므로, 계좌는 여기서 값으로 확정해 컨텍스트에 담아 내보낸다
    @Test
    void createEwaTransfer_PENDING_저장하고_계좌를_값으로_반환() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        Worker worker = mock(Worker.class);
        when(ewaRequestRepository.findById(1L)).thenReturn(Optional.of(ewaRequest));
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(ewaRequest.getWorker()).thenReturn(worker);
        when(worker.toTransferAccount()).thenReturn(new TransferAccount("004", "1234-5678", "박사원"));

        EwaTransferContext result = ewaTransferProcessor.createEwaTransfer(1L);

        assertEquals(BigDecimal.valueOf(50000), result.amount());
        assertEquals(new TransferAccount("004", "1234-5678", "박사원"), result.transferAccount());
        verify(ewaTransferRepository).save(any(EwaTransfer.class));
    }

    @Test
    void createEwaTransfer_EwaRequest_없으면_예외() {
        when(ewaRequestRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> ewaTransferProcessor.createEwaTransfer(1L));
    }

    @Test
    void assignMessageNo_필드저장() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.assignMessageNo(1L, "MSG-001");

        verify(ewaTransfer).assignMessageNo("MSG-001");
    }

    @Test
    void completeTransfer_COMPLETED_승인_금액은_건드리지_않음() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.completeTransfer(1L);

        verify(ewaTransfer).completed();
        verify(ewaRequest).approved();
        // 요청 시점에 이미 더해졌으므로 성공 시에는 금액을 건드리지 않는다 — 조회조차 하지 않는다
        verify(payPeriodRepository, never()).findByIdWithLock(any());
    }

    @Test
    void markPendingInquiry_PENDING_INQUIRY_상태변경() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.markPendingInquiry(1L);

        verify(ewaTransfer).markPendingInquiry();
    }

    @Test
    void failTransfer_FAILED_한도_환원() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));
        PayPeriod payPeriod = stubLockedPayPeriod(ewaRequest);

        ewaTransferProcessor.failTransfer(1L);

        verify(ewaTransfer).failed();
        verify(ewaRequest).failed();
        verify(payPeriod).subtractEwaAmount(BigDecimal.valueOf(50000));
    }

    @Test
    void unknown_UNKNOWN_EwaRequest_unknown_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.unknownTransfer(1L);

        verify(ewaTransfer).unknown();
        verify(ewaRequest).unknown();
    }

    @Test
    void receiveInterBankFailure_RETRYING_한도유지_OutBox_이벤트_저장() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(ewaTransferRepository.findByMessageNo("TX-001")).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.receiveInterBankFailure("TX-001");

        verify(ewaTransfer).retrying();
        // RETRYING은 미확정이므로 한도를 되돌리지 않는다 (EwaRequest에 접근조차 하지 않음)
        verify(ewaTransfer, never()).getEwaRequest();
        ArgumentCaptor<EwaTransferFailureOutBoxEvent> captor = ArgumentCaptor.captor();
        verify(ewaTransferFailureOutBoxRepository).save(captor.capture());
        assertEquals("TX-001", captor.getValue().getMessageNo());
        assertEquals(1L, captor.getValue().getEwaTransferId());
        assertEquals(BigDecimal.valueOf(50000), captor.getValue().getAmount());
    }

    @Test
    void completeRetry_COMPLETED_승인_금액은_건드리지_않음() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.completeRetry(1L);

        verify(ewaTransfer).completed();
        verify(ewaRequest).approved();
        // 불능통지에서 되돌리지 않았으므로 재시도 성공 시에도 다시 더하지 않는다
        verify(payPeriodRepository, never()).findByIdWithLock(any());
    }

    @Test
    void failRetry_FAILED_한도_환원() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        PayPeriod payPeriod = stubLockedPayPeriod(ewaRequest);

        ewaTransferProcessor.failRetry(1L);

        verify(ewaTransfer).failed();
        verify(ewaRequest).failed();
        verify(payPeriod).subtractEwaAmount(BigDecimal.valueOf(50000));
    }

    @Test
    void retryUnknown_EwaTransfer_UNKNOWN_EwaRequest_UNKNOWN() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.unKnownRetry(1L);

        verify(ewaTransfer).unknown();
        verify(ewaRequest).unknown();
    }
}
