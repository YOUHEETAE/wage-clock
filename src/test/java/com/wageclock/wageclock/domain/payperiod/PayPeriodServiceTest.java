package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class PayPeriodServiceTest {
    @InjectMocks
    private PayPeriodService payPeriodService;
    @Mock
    private PayPeriodRepository payPeriodRepository;
    @Mock
    private WorkSessionRepository workSessionRepository;
    @Mock
    Employment employment;
    @Mock
    PayPeriod payPeriod;
    @Mock
    Employer employer;
    @Mock
    WorkSession workSession;

    @Test
    void employmentId_권한_체크_예외(){
        when(payPeriodRepository.findByEmploymentIdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(payPeriod));
        when(payPeriod.getEmployerId()).thenReturn(2L);
        assertThrows(UnauthorizedException.class,()-> payPeriodService.closePayPeriod(1L,1L));
    }
    @Test
    void ACTIVE_payPeriod_없을_시_예외(){
        when(payPeriodRepository.findByEmploymentIdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> payPeriodService.closePayPeriod(1L, 1L));
    }
    @Test
    void workSession_WORKING_상태_존재_시_예외(){
        when(payPeriodRepository.findByEmploymentIdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(payPeriod));
        when(payPeriod.getEmployerId()).thenReturn(1L);
        when(workSessionRepository.existsByEmploymentIdAndStatus(1L,
                WorkSession.WorkSessionStatus.WORKING))
                .thenReturn(true);
        assertThrows(IllegalStateException.class, () -> payPeriodService.closePayPeriod(1L, 1L));
    }
    @Test
    void workSession_PAUSED_상태_존재_시_예외(){
        when(payPeriodRepository.findByEmploymentIdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(payPeriod));
        when(payPeriod.getEmployerId()).thenReturn(1L);
        when(workSessionRepository.existsByEmploymentIdAndStatus(1L,
                WorkSession.WorkSessionStatus.WORKING))
                .thenReturn(false);
        when(workSessionRepository.existsByEmploymentIdAndStatus(1L,
                WorkSession.WorkSessionStatus.PAUSED))
                .thenReturn(true);
        assertThrows(IllegalStateException.class, () -> payPeriodService.closePayPeriod(1L, 1L));
    }
    @Test
    void 정상_close(){
        when(workSessionRepository.existsByEmploymentIdAndStatus(1L,
                WorkSession.WorkSessionStatus.WORKING))
                .thenReturn(false);
        when(workSessionRepository.existsByEmploymentIdAndStatus(1L,
                WorkSession.WorkSessionStatus.PAUSED))
                .thenReturn(false);
        when(employment.getEmployer()).thenReturn(employer);
        when(employer.getId()).thenReturn(1L);
        PayPeriod payPeriod = new PayPeriod(employment);
        payPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        payPeriod.addEwaAmount(BigDecimal.valueOf(1000));
        when(payPeriodRepository.findByEmploymentIdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(payPeriod));
        payPeriodService.closePayPeriod(1L, 1L);
        assertEquals(PayPeriod.PayPeriodStatus.CLOSED, payPeriod.getStatus());
        assertEquals(LocalDate.now(), payPeriod.getPeriodEnd());
        assertEquals(0, payPeriod.getActualPayAmount().compareTo(BigDecimal.valueOf(9000)));
    }

    @Test
    void summary_ACTIVE_payPeriod_없을_시_예외() {
        when(payPeriodRepository.findByEmploymentIdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> payPeriodService.getPayPeriodSummaryResponse(1L, 1L));
    }

    @Test
    void summary_workerId_권한_체크_예외() {
        when(payPeriodRepository.findByEmploymentIdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(payPeriod));
        when(payPeriod.getWorkerId()).thenReturn(2L);
        when(payPeriod.getEmployerId()).thenReturn(2L);
        assertThrows(UnauthorizedException.class, () -> payPeriodService.getPayPeriodSummaryResponse(1L, 1L));
    }

    @Test
    void summary_현재_세션_없을_때_정상_조회() {
        when(employment.getWorkerId()).thenReturn(1L);
        when(employment.getEmployer()).thenReturn(employer);
        when(employer.getId()).thenReturn(2L);
        PayPeriod realPayPeriod = new PayPeriod(employment);
        realPayPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        realPayPeriod.addEwaAmount(BigDecimal.valueOf(1000));
        when(payPeriodRepository.findByEmploymentIdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(realPayPeriod));
        when(workSessionRepository.findByEmploymentIdAndStatusNot(1L, WorkSession.WorkSessionStatus.COMPLETED))
                .thenReturn(Optional.empty());

        PayPeriodSummaryResponse response = payPeriodService.getPayPeriodSummaryResponse(1L, 1L);
        assertEquals(0, response.totalEarnedAmount().compareTo(BigDecimal.valueOf(10000)));
        assertEquals(0, response.totalEwaAmount().compareTo(BigDecimal.valueOf(1000)));
        assertEquals(0, response.remainingEwaLimit().compareTo(BigDecimal.valueOf(2000)));
    }

    @Test
    void summary_현재_세션_있을_때_currentEarned_반영() {
        when(employment.getWorkerId()).thenReturn(1L);
        when(employment.getEmployer()).thenReturn(employer);
        when(employer.getId()).thenReturn(2L);
        PayPeriod realPayPeriod = new PayPeriod(employment);
        realPayPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        realPayPeriod.addEwaAmount(BigDecimal.valueOf(1000));
        when(payPeriodRepository.findByEmploymentIdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(realPayPeriod));
        when(workSessionRepository.findByEmploymentIdAndStatusNot(1L, WorkSession.WorkSessionStatus.COMPLETED))
                .thenReturn(Optional.of(workSession));
        when(workSession.getCurrentEarnedAmount()).thenReturn(BigDecimal.valueOf(5000));

        PayPeriodSummaryResponse response = payPeriodService.getPayPeriodSummaryResponse(1L, 1L);
        assertEquals(0, response.totalEarnedAmount().compareTo(BigDecimal.valueOf(15000)));
        assertEquals(0, response.totalEwaAmount().compareTo(BigDecimal.valueOf(1000)));
        assertEquals(0, response.remainingEwaLimit().compareTo(BigDecimal.valueOf(3500)));
    }
}
