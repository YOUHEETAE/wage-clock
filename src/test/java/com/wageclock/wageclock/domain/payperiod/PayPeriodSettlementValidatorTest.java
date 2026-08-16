package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestRepository;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRepository;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayPeriodCloseValidatorTest {

    @Mock WorkSessionRepository workSessionRepository;
    @Mock EwaRequestRepository ewaRequestRepository;
    @Mock EwaTransferRepository ewaTransferRepository;
    @Mock PayPeriod payPeriod;
    @InjectMocks PayPeriodCloseValidator payPeriodCloseValidator;

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

        assertThrows(IllegalStateException.class, () -> payPeriodCloseValidator.validate(payPeriod));
    }

    // 정산 후 확정되면 totalEwaAmount가 바뀌어 이미 이체한 금액과 어긋난다
    @Test
    void 미확정_EwaRequest_존재_시_예외() {
        세션_없음();
        when(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(eq(payPeriod), anyList())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> payPeriodCloseValidator.validate(payPeriod));
    }

    // EwaRequest가 APPROVED여도 이체가 진행 중이면 결과는 아직 미확정이다
    @Test
    void 미확정_EwaTransfer_존재_시_예외() {
        세션_없음();
        when(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(eq(payPeriod), anyList())).thenReturn(false);
        when(ewaTransferRepository.existsByEwaRequest_PayPeriodAndStatusNotIn(eq(payPeriod), anyList()))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> payPeriodCloseValidator.validate(payPeriod));
    }

    @Test
    void 셋_다_없으면_통과() {
        세션_없음();
        when(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(eq(payPeriod), anyList())).thenReturn(false);
        when(ewaTransferRepository.existsByEwaRequest_PayPeriodAndStatusNotIn(eq(payPeriod), anyList()))
                .thenReturn(false);

        assertDoesNotThrow(() -> payPeriodCloseValidator.validate(payPeriod));
    }

    // 확정 상태만 나열하고 나머지를 미확정으로 보므로, 새 상태가 생겨도 기본값이 "막는다"가 된다
    @Test
    void EwaRequest_확정_상태만_제외한다() {
        세션_없음();
        when(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(eq(payPeriod),
                eq(List.of(EwaRequest.EwaRequestStatus.FAILED,
                        EwaRequest.EwaRequestStatus.APPROVED,
                        EwaRequest.EwaRequestStatus.REJECTED)))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> payPeriodCloseValidator.validate(payPeriod));
    }

    @Test
    void EwaTransfer_확정_상태만_제외한다() {
        세션_없음();
        when(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(any(), anyList())).thenReturn(false);
        when(ewaTransferRepository.existsByEwaRequest_PayPeriodAndStatusNotIn(eq(payPeriod),
                eq(List.of(EwaTransfer.EwaTransferStatus.FAILED,
                        EwaTransfer.EwaTransferStatus.COMPLETED)))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> payPeriodCloseValidator.validate(payPeriod));
    }
}
