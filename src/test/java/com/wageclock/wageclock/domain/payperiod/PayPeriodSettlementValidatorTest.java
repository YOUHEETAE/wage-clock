package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestRepository;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRepository;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayPeriodSettlementValidatorTest {

    @Mock WorkSessionRepository workSessionRepository;
    @Mock EwaRequestRepository ewaRequestRepository;
    @Mock EwaTransferRepository ewaTransferRepository;
    @Mock WorkerRepository workerRepository;
    @Mock PayPeriod payPeriod;
    @InjectMocks
    PayPeriodSettlementValidator payPeriodSettlementValidator;

    private void 세션_없음() {
        when(payPeriod.getEmploymentId()).thenReturn(1L);
        when(workSessionRepository.existsByEmploymentIdAndStatusNot(1L,
                WorkSession.WorkSessionStatus.COMPLETED)).thenReturn(false);
    }

    // 퇴근 시 적립액이 이미 마감된 PayPeriod에 더해져 지급되지 않는다
    @Test
    void 완료되지_않은_workSession_존재_시_예외() {
        when(payPeriod.getEmploymentId()).thenReturn(1L);
        when(workSessionRepository.existsByEmploymentIdAndStatusNot(1L,
                WorkSession.WorkSessionStatus.COMPLETED)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> payPeriodSettlementValidator.validate(payPeriod));
    }

    // 정산 후 확정되면 totalEwaAmount가 바뀌어 이미 이체한 금액과 어긋난다
    @Test
    void 미확정_EwaRequest_존재_시_예외() {
        세션_없음();
        when(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(eq(payPeriod), anyList())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> payPeriodSettlementValidator.validate(payPeriod));
    }

    // EwaRequest가 APPROVED여도 이체가 진행 중이면 결과는 아직 미확정이다
    @Test
    void 미확정_EwaTransfer_존재_시_예외() {
        세션_없음();
        when(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(eq(payPeriod), anyList())).thenReturn(false);
        when(ewaTransferRepository.existsByEwaRequest_PayPeriodAndStatusNotIn(eq(payPeriod), anyList()))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> payPeriodSettlementValidator.validate(payPeriod));
    }

    @Test
    void 셋_다_없으면_통과() {
        세션_없음();
        when(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(eq(payPeriod), anyList())).thenReturn(false);
        when(ewaTransferRepository.existsByEwaRequest_PayPeriodAndStatusNotIn(eq(payPeriod), anyList()))
                .thenReturn(false);

        assertDoesNotThrow(() -> payPeriodSettlementValidator.validate(payPeriod));
    }

    // 확정 상태만 나열하고 나머지를 미확정으로 보므로, 새 상태가 생겨도 기본값이 "막는다"가 된다
    @Test
    void EwaRequest_확정_상태만_제외한다() {
        세션_없음();
        when(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(eq(payPeriod),
                eq(List.of(EwaRequest.EwaRequestStatus.FAILED,
                        EwaRequest.EwaRequestStatus.APPROVED,
                        EwaRequest.EwaRequestStatus.REJECTED)))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> payPeriodSettlementValidator.validate(payPeriod));
    }

    @Test
    void EwaTransfer_확정_상태만_제외한다() {
        세션_없음();
        when(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(any(), anyList())).thenReturn(false);
        when(ewaTransferRepository.existsByEwaRequest_PayPeriodAndStatusNotIn(eq(payPeriod),
                eq(List.of(EwaTransfer.EwaTransferStatus.FAILED,
                        EwaTransfer.EwaTransferStatus.COMPLETED)))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> payPeriodSettlementValidator.validate(payPeriod));
    }

    private Worker 근로자(String name, boolean 계좌등록) {
        Worker worker = Worker.builder().name(name).email(name + "@test.com").password("password").build();
        if (계좌등록) {
            worker.registerAccountInfo("1234-5678", "004", name);
        }
        return worker;
    }

    @Test
    void 계좌_미등록_근로자가_있으면_예외() {
        PayPeriod payPeriod = mock(PayPeriod.class);
        when(payPeriod.getWorkerId()).thenReturn(1L);
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of(근로자("김철수", false)));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> payPeriodSettlementValidator.validateAccounts(List.of(payPeriod)));

        // 사장이 누구에게 연락해야 하는지 알아야 "빨리 등록하라"는 유도가 작동한다
        assertTrue(e.getMessage().contains("김철수"));
    }

    // 첫 명에서 끊으면 사장이 한 명씩 고쳐가며 재시도하게 된다
    @Test
    void 미등록자가_여럿이면_이름을_모두_담는다() {
        PayPeriod payPeriod1 = mock(PayPeriod.class);
        PayPeriod payPeriod2 = mock(PayPeriod.class);
        PayPeriod payPeriod3 = mock(PayPeriod.class);
        when(payPeriod1.getWorkerId()).thenReturn(1L);
        when(payPeriod2.getWorkerId()).thenReturn(2L);
        when(payPeriod3.getWorkerId()).thenReturn(3L);
        when(workerRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(
                List.of(근로자("김철수", false), 근로자("이영희", true), 근로자("박민수", false)));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> payPeriodSettlementValidator.validateAccounts(
                        List.of(payPeriod1, payPeriod2, payPeriod3)));

        assertTrue(e.getMessage().contains("김철수, 박민수"));
        assertFalse(e.getMessage().contains("이영희"));
    }

    @Test
    void 전원_계좌_등록이면_통과() {
        PayPeriod payPeriod1 = mock(PayPeriod.class);
        PayPeriod payPeriod2 = mock(PayPeriod.class);
        when(payPeriod1.getWorkerId()).thenReturn(1L);
        when(payPeriod2.getWorkerId()).thenReturn(2L);
        when(workerRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(근로자("김철수", true), 근로자("이영희", true)));

        assertDoesNotThrow(() -> payPeriodSettlementValidator.validateAccounts(
                List.of(payPeriod1, payPeriod2)));
    }

    // 이체 전문에 세 값이 모두 실리므로 하나라도 비면 은행이 거부한다
    @Test
    void 계좌_정보가_일부만_있으면_미등록으로_본다() {
        PayPeriod payPeriod = mock(PayPeriod.class);
        Worker worker = Worker.builder().name("김철수").email("kim@test.com").password("password").build();
        worker.registerAccountInfo("1234-5678", "004", null); // 예금주명 누락
        when(payPeriod.getWorkerId()).thenReturn(1L);
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of(worker));

        assertThrows(IllegalStateException.class,
                () -> payPeriodSettlementValidator.validateAccounts(List.of(payPeriod)));
    }
}
