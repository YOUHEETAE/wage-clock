package com.wageclock.wageclock.domain.EwaTransfer;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
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
    void assignTransferId_COMPLETED_EwaRequest_승인_PayPeriod_금액추가() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        PayPeriod payPeriod = mock(PayPeriod.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(ewaRequest.getPayPeriod()).thenReturn(payPeriod);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.assignTransferId("TX-001", 1L);

        verify(ewaTransfer).assignTransferId("TX-001");
        verify(ewaRequest).approved();
        verify(payPeriod).addEwaAmount(BigDecimal.valueOf(50000));
    }

    @Test
    void markPendingInquiry_PENDING_INQUIRY_상태변경() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.markPendingInquiry("MSG-001", 1L);

        verify(ewaTransfer).markPendingInquiry("MSG-001");
    }

    @Test
    void markFailed_FAILED_EwaRequest_failed_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.markFailed(1L);

        verify(ewaTransfer).markFailed();
        verify(ewaRequest).failed();
    }

    @Test
    void markUnknown_UNKNOWN_EwaRequest_unknown_호출() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.markUnknown(1L);

        verify(ewaTransfer).markUnknown();
        verify(ewaRequest).unknown();
    }

    @Test
    void receiveInterBankFailure_RETRYING_금액환원_OutBox_이벤트_저장() {
        EwaRequest ewaRequest = mock(EwaRequest.class);
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransfer.getId()).thenReturn(1L);
        when(ewaTransfer.getAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(ewaTransfer.getEwaRequest()).thenReturn(ewaRequest);
        when(ewaTransferRepository.findByTransferId("TX-001")).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.receiveInterBankFailure("TX-001");

        verify(ewaTransfer).markRetrying();
        verify(ewaRequest).refundEwa(BigDecimal.valueOf(50000));
        ArgumentCaptor<EwaTransferFailureOutBoxEvent> captor = ArgumentCaptor.captor();
        verify(ewaTransferFailureOutBoxRepository).save(captor.capture());
        assertEquals("TX-001", captor.getValue().getTransferId());
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

        ewaTransferProcessor.completeRetry("TX-002", 1L);

        verify(ewaTransfer).assignTransferId("TX-002");
        verify(payPeriod).addEwaAmount(BigDecimal.valueOf(50000));
        verify(ewaRequest, never()).approved();
    }

    @Test
    void markRetryFailed_EwaTransfer_FAILED_EwaRequest_미변경() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.markRetryFailed(1L);

        verify(ewaTransfer).markFailed();
        verify(ewaTransfer, never()).getEwaRequest();
    }

    @Test
    void markRetryUnknown_EwaTransfer_UNKNOWN_EwaRequest_미변경() {
        EwaTransfer ewaTransfer = mock(EwaTransfer.class);
        when(ewaTransferRepository.findById(1L)).thenReturn(Optional.of(ewaTransfer));

        ewaTransferProcessor.markRetryUnknown(1L);

        verify(ewaTransfer).markUnknown();
        verify(ewaTransfer, never()).getEwaRequest();
    }
}
