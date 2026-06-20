package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
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
    @InjectMocks EwaTransferProcessor ewaTransferProcessor;

    @Test
    void createEwaTransfer_PENDING_상태로_저장() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(50000));

        EwaTransfer result = ewaTransferProcessor.createEwaTransfer(ewaRequest);

        assertEquals(EwaTransfer.EwaTransferStatus.PENDING, result.getStatus());
        assertEquals(BigDecimal.valueOf(50000), result.getAmount());
        verify(ewaTransferRepository).save(any(EwaTransfer.class));
    }

    @Test
    void assignMessageNo_필드저장() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.assignMessageNo(1L, "MSG-001");

        verify(ewaTransfer).assignMessageNo("MSG-001");
    }

    @Test
    void completed_COMPLETED_EwaRequest_승인_PayPeriod_금액추가() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        PayPeriod payPeriod = mock(PayPeriod.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(ewaRequest.getPayPeriod()).thenReturn(payPeriod);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.completeTransfer(1L);

        verify(ewaTransfer).completed();
        verify(ewaRequest).approved();
        verify(payPeriod).addEwaAmount(BigDecimal.valueOf(50000));
    }

    @Test
    void markPendingInquiry_PENDING_INQUIRY_상태변경() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.markPendingInquiry(1L);

        verify(ewaTransfer).markPendingInquiry();
    }

    @Test
    void failed_FAILED_EwaRequest_failed_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.failTransfer(1L);

        verify(ewaTransfer).failed();
        verify(ewaRequest).failed();
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
    void receiveInterBankFailure_RETRYING_금액환원_OutBox_이벤트_저장() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransferRepository.findByMessageNo("TX-001")).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.receiveInterBankFailure("TX-001");

        verify(ewaTransfer).retrying();
        verify(ewaRequest).refundEwa(BigDecimal.valueOf(50000));
        ArgumentCaptor<EwaTransferFailureOutBoxEvent> captor = ArgumentCaptor.captor();
        verify(ewaTransferFailureOutBoxRepository).save(captor.capture());
        assertEquals("TX-001", captor.getValue().getMessageNo());
        assertEquals(1L, captor.getValue().getEwaTransferId());
        assertEquals(BigDecimal.valueOf(50000), captor.getValue().getAmount());
    }

    @Test
    void completeRetry_COMPLETED_PayPeriod_재차감() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        PayPeriod payPeriod = mock(PayPeriod.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(ewaRequest.getPayPeriod()).thenReturn(payPeriod);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.completeRetry(1L);

        verify(ewaTransfer).completed();
        verify(payPeriod).addEwaAmount(BigDecimal.valueOf(50000));
        verify(ewaRequest, never()).approved();
    }

    @Test
    void retryFailed_EwaTransfer_FAILED_EwaRequest_미변경() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.failRetry(1L);

        verify(ewaTransfer).failed();
        verify(ewaTransfer, never()).getEwaRequest();
    }

    @Test
    void retryUnknown_EwaTransfer_UNKNOWN_EwaRequest_미변경() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.unKnownRetry(1L);

        verify(ewaTransfer).unknown();
        verify(ewaTransfer, never()).getEwaRequest();
    }
}
