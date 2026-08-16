package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.outbox.BulkSettlementOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.BulkSettlementOutBoxEventRepository;
import com.wageclock.wageclock.domain.outbox.InterBankFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.InterBankFailureOutBoxEventRepository;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodSettlementValidator;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
    @Mock
    PayPeriodSettlementValidator payPeriodSettlementValidator;
    @Mock EmploymentRepository employmentRepository;
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
        verify(payPeriod1).startSettling();
        verify(payPeriod2).startSettling();
        verify(bulkSettlementRepository).save(any(BulkSettlement.class));
        verify(bulkSettlementOutBoxEventRepository).save(any(BulkSettlementOutBoxEvent.class));
    }

    // 출근(clockIn)과 직렬화하려면 PayPeriod 조회보다 먼저 Employment를 잠가야 한다
    @Test
    void createBulkSettlement_Employment_락을_먼저_잡는다() {
        PayPeriod payPeriod = mock(PayPeriod.class);
        when(payPeriod.getId()).thenReturn(1L);
        when(payPeriod.getActualPayAmount()).thenReturn(BigDecimal.valueOf(50000));
        when(payPeriod.getEmployerName()).thenReturn("테스트사업장");
        when(payPeriodRepository.findAllByEmploymentIdInAndEmployerIdAndStatusWithLock(anyList(), anyLong()))
                .thenReturn(List.of(payPeriod));
        when(bulkSettlementItemRepository.existsByPayPeriod_IdAndBulkSettlement_StatusNotIn(anyLong(), anyList()))
                .thenReturn(false);

        bulkSettlementProcessor.createBulkSettlement(List.of(1L), 1L);

        InOrder inOrder = inOrder(employmentRepository, payPeriodRepository);
        inOrder.verify(employmentRepository).findAllByIdInWithLock(List.of(1L));
        inOrder.verify(payPeriodRepository)
                .findAllByEmploymentIdInAndEmployerIdAndStatusWithLock(anyList(), anyLong());
    }

    // 한 명이라도 걸리면 전체를 막는다 — 승인 금액과 실제 이체 구성이 어긋나면 안 된다
    @Test
    void createBulkSettlement_검증실패_전원_전이안됨() {
        PayPeriod payPeriod1 = mock(PayPeriod.class);
        PayPeriod payPeriod2 = mock(PayPeriod.class);
        when(payPeriod1.getId()).thenReturn(1L);
        when(payPeriodRepository.findAllByEmploymentIdInAndEmployerIdAndStatusWithLock(anyList(), anyLong()))
                .thenReturn(List.of(payPeriod1, payPeriod2));
        when(bulkSettlementItemRepository.existsByPayPeriod_IdAndBulkSettlement_StatusNotIn(anyLong(), anyList()))
                .thenReturn(false);
        doThrow(new IllegalStateException("Working WorkSession exists"))
                .when(payPeriodSettlementValidator).validate(payPeriod1);

        assertThrows(IllegalStateException.class,
                () -> bulkSettlementProcessor.createBulkSettlement(List.of(1L, 2L), 1L));

        verify(payPeriod1, never()).startSettling();
        verify(payPeriod2, never()).startSettling();
        verify(bulkSettlementRepository, never()).save(any());
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
    void completeItem_정상_COMPLETED_PayPeriod_close() {
        PayPeriod payPeriod = mock(PayPeriod.class);
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(mock(BulkSettlement.class))
                .payPeriod(payPeriod)
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));

        bulkSettlementProcessor.completeItem(1L);

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.COMPLETED, item.getStatus());
        verify(payPeriod).close();
    }

    @Test
    void assignMessageNo_필드저장() {
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(mock(BulkSettlement.class))
                .payPeriod(mock(PayPeriod.class))
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));

        bulkSettlementProcessor.assignMessageNo(1L, "MSG-001");

        assertEquals("MSG-001", item.getMessageNo());
    }

    @Test
    void markPendingInquiry_정상_PENDING_INQUIRY() {
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(mock(BulkSettlement.class))
                .payPeriod(mock(PayPeriod.class))
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));

        bulkSettlementProcessor.markPendingInquiry(1L);

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY, item.getStatus());
    }

    // 확정 실패는 돈이 안 나간 게 확실하므로 되돌려 재정산 대상이 되게 한다
    @Test
    void failItem_정상_FAILED_PayPeriod_reopen() {
        PayPeriod payPeriod = mock(PayPeriod.class);
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(mock(BulkSettlement.class))
                .payPeriod(payPeriod)
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));

        bulkSettlementProcessor.failItem(1L);

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.FAILED, item.getStatus());
        verify(payPeriod).reopen();
    }

    // 미확정(PENDING_INQUIRY·UNKNOWN)은 되돌리지 않는다. 재정산되면 이중 송금이 된다.
    @Test
    void markPendingInquiry_PayPeriod_reopen_안함() {
        PayPeriod payPeriod = mock(PayPeriod.class);
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(mock(BulkSettlement.class))
                .payPeriod(payPeriod)
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));

        bulkSettlementProcessor.markPendingInquiry(1L);

        verify(payPeriod, never()).reopen();
    }

    @Test
    void unknownItem_PayPeriod_reopen_안함() {
        PayPeriod payPeriod = mock(PayPeriod.class);
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(mock(BulkSettlement.class))
                .payPeriod(payPeriod)
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));

        bulkSettlementProcessor.unknownItem(1L);

        verify(payPeriod, never()).reopen();
    }

    // 사장이 입금하지 않아 무산된 정산 — 전원 되돌리지 않으면 SETTLING에 갇힌다
    @Test
    void failPayment_전원_reopen_후_PAYMENT_FAILED() {
        PayPeriod payPeriod1 = mock(PayPeriod.class);
        PayPeriod payPeriod2 = mock(PayPeriod.class);
        BulkSettlement bulkSettlement = BulkSettlement.builder()
                .employerId(1L).portOnePaymentId("BULK-test").totalAmount(BigDecimal.valueOf(80000)).build();
        bulkSettlement.addItem(BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(payPeriod1).amount(BigDecimal.valueOf(50000)).build());
        bulkSettlement.addItem(BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(payPeriod2).amount(BigDecimal.valueOf(30000)).build());
        when(bulkSettlementRepository.findByPortOnePaymentId("BULK-test")).thenReturn(Optional.of(bulkSettlement));

        bulkSettlementProcessor.failPayment("BULK-test");

        verify(payPeriod1).reopen();
        verify(payPeriod2).reopen();
        assertEquals(BulkSettlement.BulkSettlementStatus.PAYMENT_FAILED, bulkSettlement.getStatus());
    }

    // PG가 웹훅을 재전송하면 두 번 들어온다. 두 번째 reopen은 ACTIVE라 예외가 난다.
    @Test
    void failPayment_이미_PAYMENT_FAILED면_reopen_안함() {
        PayPeriod payPeriod = mock(PayPeriod.class);
        BulkSettlement bulkSettlement = BulkSettlement.builder()
                .employerId(1L).portOnePaymentId("BULK-test").totalAmount(BigDecimal.valueOf(50000)).build();
        bulkSettlement.addItem(BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(payPeriod).amount(BigDecimal.valueOf(50000)).build());
        bulkSettlement.paymentFailed();
        when(bulkSettlementRepository.findByPortOnePaymentId("BULK-test")).thenReturn(Optional.of(bulkSettlement));

        bulkSettlementProcessor.failPayment("BULK-test");

        verify(payPeriod, never()).reopen();
    }

    @Test
    void unknownItem_정상_UNKNOWN() {
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(mock(BulkSettlement.class))
                .payPeriod(mock(PayPeriod.class))
                .amount(BigDecimal.valueOf(50000))
                .build();
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));

        bulkSettlementProcessor.unknownItem(1L);

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.UNKNOWN, item.getStatus());
    }

    @Test
    void completeSettlement_전체_COMPLETED() {
        BulkSettlement bulkSettlement = BulkSettlement.builder()
                .employerId(1L).portOnePaymentId("BULK-test").totalAmount(BigDecimal.valueOf(80000)).build();
        BulkSettlementItem item1 = BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(mock(PayPeriod.class)).amount(BigDecimal.valueOf(50000)).build();
        BulkSettlementItem item2 = BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(mock(PayPeriod.class)).amount(BigDecimal.valueOf(30000)).build();
        item1.completed();
        item2.completed();
        bulkSettlement.addItem(item1);
        bulkSettlement.addItem(item2);
        when(bulkSettlementRepository.findByPortOnePaymentIdWithLock("BULK-test")).thenReturn(Optional.of(bulkSettlement));

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
        item1.completed();
        item2.failed();
        bulkSettlement.addItem(item1);
        bulkSettlement.addItem(item2);
        when(bulkSettlementRepository.findByPortOnePaymentIdWithLock("BULK-test")).thenReturn(Optional.of(bulkSettlement));

        bulkSettlementProcessor.completeSettlement("BULK-test");

        assertNotEquals(BulkSettlement.BulkSettlementStatus.COMPLETED, bulkSettlement.getStatus());
        verify(bulkSettlementRepository, never()).save(any());
    }

    @Test
    void receiveInterBankFailure_정상_RETRYING_세틀먼트도_RETRYING_아웃박스생성() {
        BulkSettlement bulkSettlement = mock(BulkSettlement.class);
        when(bulkSettlement.getPortOnePaymentId()).thenReturn("BULK-test");
        when(bulkSettlement.getId()).thenReturn(1L);
        when(bulkSettlement.getStatus()).thenReturn(BulkSettlement.BulkSettlementStatus.COMPLETED);
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(mock(PayPeriod.class)).amount(BigDecimal.valueOf(50000)).build();
        item.assignMessageNo("TX-001");
        when(bulkSettlementItemRepository.findByMessageNo("TX-001")).thenReturn(Optional.of(item));
        when(bulkSettlementRepository.findByPortOnePaymentIdWithLock("BULK-test")).thenReturn(Optional.of(bulkSettlement));

        bulkSettlementProcessor.receiveInterBankFailure("TX-001");

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.RETRYING, item.getStatus());
        verify(bulkSettlement).retrying();
        ArgumentCaptor<InterBankFailureOutBoxEvent> captor = ArgumentCaptor.captor();
        verify(interBankFailureOutBoxEventRepository).save(captor.capture());
        assertEquals("TX-001", captor.getValue().getMessageNo());
        assertEquals("BULK-test", captor.getValue().getPortOnePaymentId());
    }

    @Test
    void completeRetry_아이템_COMPLETED_세틀먼트도_재확인() {
        BulkSettlement bulkSettlement = BulkSettlement.builder()
                .employerId(1L).portOnePaymentId("BULK-test").totalAmount(BigDecimal.valueOf(50000)).build();
        BulkSettlementItem item = BulkSettlementItem.builder()
                .bulkSettlement(bulkSettlement).payPeriod(mock(PayPeriod.class)).amount(BigDecimal.valueOf(50000)).build();
        bulkSettlement.addItem(item);
        when(bulkSettlementItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(bulkSettlementRepository.findByPortOnePaymentIdWithLock("BULK-test")).thenReturn(Optional.of(bulkSettlement));

        bulkSettlementProcessor.completeRetry(1L);

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.COMPLETED, item.getStatus());
        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED, bulkSettlement.getStatus());
    }
}
