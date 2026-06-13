package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.outbox.BulkSettlementOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.BulkSettlementOutBoxEventRepository;
import com.wageclock.wageclock.domain.outbox.InterBankFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.InterBankFailureOutBoxEventRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkSettlementProcessorTest {

    @Mock PayPeriodRepository payPeriodRepository;
    @Mock BulkSettlementRepository bulkSettlementRepository;
    @Mock BulkSettlementItemRepository bulkSettlementItemRepository;
    @Mock BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;
    @Mock InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    @InjectMocks BulkSettlementProcessor bulkSettlementProcessor;

    @Test
    void createBulkSettlement_정상_생성() {
        PayPeriod payPeriod1 = mock(PayPeriod.class);
        PayPeriod payPeriod2 = mock(PayPeriod.class);
        when(payPeriod1.getId()).thenReturn(1L);
        when(payPeriod2.getId()).thenReturn(2L);
        when(payPeriod1.getActualPayAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(payPeriod2.getActualPayAmount()).thenReturn(BigDecimal.valueOf(30000));
        when(payPeriod1.getEmployerName()).thenReturn("테스트사업장");
        when(payPeriodRepository.findAllByEmploymentIdInAndEmployerIdAndStatusWithLock(anyList(), anyLong()))
                .thenReturn(List.of(payPeriod1, payPeriod2));
        when(bulkSettlementItemRepository.existsByPayPeriod_IdAndBulkSettlement_StatusNotIn(anyLong(), anyList()))
                .thenReturn(false);

        BulkSettlement result = bulkSettlementProcessor.createBulkSettlement(List.of(1L, 2L), 1L);

        assertEquals(BigDecimal.valueOf(80000), result.getTotalAmount());
        assertEquals(2, result.getItems().size());
        verify(bulkSettlementRepository).save(any(BulkSettlement.class));
        verify(bulkSettlementOutBoxEventRepository).save(any(BulkSettlementOutBoxEvent.class));
    }

    @Test
    void createBulkSettlement_인원불일치_UnauthorizedException() {
        when(payPeriodRepository.findAllByEmploymentIdInAndEmployerIdAndStatusWithLock(anyList(), anyLong()))
                .thenReturn(List.of(mock(PayPeriod.class)));

        assertThrows(UnauthorizedException.class,
                () -> bulkSettlementProcessor.createBulkSettlement(List.of(1L, 2L), 1L));
    }

    @Test
    void createBulkSettlement_중복정산_DuplicateException() {
        PayPeriod payPeriod = mock(PayPeriod.class);
        when(payPeriod.getId()).thenReturn(1L);
        when(payPeriodRepository.findAllByEmploymentIdInAndEmployerIdAndStatusWithLock(anyList(), anyLong()))
                .thenReturn(List.of(payPeriod));
        when(bulkSettlementItemRepository.existsByPayPeriod_IdAndBulkSettlement_StatusNotIn(anyLong(), anyList()))
                .thenReturn(true);

        assertThrows(DuplicateException.class,
                () -> bulkSettlementProcessor.createBulkSettlement(List.of(1L), 1L));
    }

    @Test
    void assignTransferId_정상_COMPLETED_PayPeriod_close() {
        PayPeriod payPeriod = mock(PayPeriod.class);
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(mock(BulkSettlement.class))
                .payPeriod(payPeriod)
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));

        bulkSettlementProcessor.assignTransferId(1L, "TX-001");

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.COMPLETED, item.getStatus());
        assertEquals("TX-001", item.getTransferId());
        verify(payPeriod).close();
    }

    @Test
    void assignTransferId_null_IllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> bulkSettlementProcessor.assignTransferId(1L, null));
    }

    @Test
    void markPendingInquiry_정상_PENDING_INQUIRY() {
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(mock(BulkSettlement.class))
                .payPeriod(mock(PayPeriod.class))
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));

        bulkSettlementProcessor.markPendingInquiry(1L, "MSG-001");

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY, item.getStatus());
        assertEquals("MSG-001", item.getPendingMessageNo());
    }

    @Test
    void markFailed_정상_FAILED() {
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(mock(BulkSettlement.class))
                .payPeriod(mock(PayPeriod.class))
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));

        bulkSettlementProcessor.markFailed(1L);

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.FAILED, item.getStatus());
    }

    @Test
    void completeSettlement_전체_COMPLETED() {
        BulkSettlement bulkSettlement = BulkSettlement.builder()
                .employerId(1L).portOnePaymentId("BULK-test").totalAmount(BigDecimal.valueOf(80000)).build();
        BulkSettlementItem item1 = BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(mock(PayPeriod.class)).amount(BigDecimal.valueOf(50000)).build();
        BulkSettlementItem item2 = BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(mock(PayPeriod.class)).amount(BigDecimal.valueOf(30000)).build();
        item1.assignTransferId("TX-001");
        item2.assignTransferId("TX-002");
        bulkSettlement.addItem(item1);
        bulkSettlement.addItem(item2);
        when(bulkSettlementRepository.findByPortOnePaymentId("BULK-test")).thenReturn(Optional.of(bulkSettlement));

        bulkSettlementProcessor.completeSettlement("BULK-test");

        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED, bulkSettlement.getStatus());
    }

    @Test
    void completeSettlement_일부_FAILED_완료안됨() {
        BulkSettlement bulkSettlement = BulkSettlement.builder()
                .employerId(1L).portOnePaymentId("BULK-test").totalAmount(BigDecimal.valueOf(80000)).build();
        BulkSettlementItem item1 = BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(mock(PayPeriod.class)).amount(BigDecimal.valueOf(50000)).build();
        BulkSettlementItem item2 = BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(mock(PayPeriod.class)).amount(BigDecimal.valueOf(30000)).build();
        item1.assignTransferId("TX-001");
        item2.markFailed();
        bulkSettlement.addItem(item1);
        bulkSettlement.addItem(item2);
        when(bulkSettlementRepository.findByPortOnePaymentId("BULK-test")).thenReturn(Optional.of(bulkSettlement));

        bulkSettlementProcessor.completeSettlement("BULK-test");

        assertNotEquals(BulkSettlement.BulkSettlementStatus.COMPLETED, bulkSettlement.getStatus());
        verify(bulkSettlementRepository, never()).save(any());
    }

    @Test
    void receiveInterBankFailure_정상_FAILED_아웃박스생성() {
        BulkSettlement bulkSettlement = mock(BulkSettlement.class);
        when(bulkSettlement.getPortOnePaymentId()).thenReturn("BULK-test");
        when(bulkSettlement.getId()).thenReturn(1L);
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(mock(PayPeriod.class)).amount(BigDecimal.valueOf(50000)).build();
        item.assignTransferId("TX-001");
        when(bulkSettlementItemRepository.findByTransferId("TX-001")).thenReturn(Optional.of(item));

        bulkSettlementProcessor.receiveInterBankFailure("TX-001");

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.FAILED, item.getStatus());
        ArgumentCaptor<InterBankFailureOutBoxEvent> captor = ArgumentCaptor.captor();
        verify(interBankFailureOutBoxEventRepository).save(captor.capture());
        assertEquals("TX-001", captor.getValue().getTransferId());
        assertEquals("BULK-test", captor.getValue().getPortOnePaymentId());
    }
}
