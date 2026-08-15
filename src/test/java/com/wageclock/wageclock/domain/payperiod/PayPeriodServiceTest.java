package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.worker.Worker;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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
    private PayPeriodSummaryRepository payPeriodSummaryRepository;
    @Mock
    Employment employment;
    @Mock
    PayPeriod payPeriod;
    @Mock
    WorkSession workSession;
    @Mock
    Worker worker;

    @Test
    void summary_ACTIVE_payPeriod_없을_시_예외() {
        when(payPeriodRepository.findByEmployment_IdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> payPeriodService.getPayPeriodSummaryResponse(1L, 1L));
    }

    @Test
    void summary_workerId_권한_체크_예외() {
        when(payPeriodRepository.findByEmployment_IdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(payPeriod));
        when(payPeriod.getWorkerId()).thenReturn(2L);
        assertThrows(UnauthorizedException.class, () -> payPeriodService.getPayPeriodSummaryResponse(1L, 1L));
    }

    @Test
    void summary_현재_세션_없을_때_정상_조회() {
        when(employment.getWorkerId()).thenReturn(1L);
        when(employment.getId()).thenReturn(1L);
        when(employment.getWorker()).thenReturn(worker);
        when(worker.getName()).thenReturn("박사원");
        PayPeriod realPayPeriod = new PayPeriod(employment);
        realPayPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        realPayPeriod.addEwaAmount(BigDecimal.valueOf(1000));
        when(payPeriodRepository.findByEmployment_IdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(realPayPeriod));
        when(workSessionRepository.findByEmploymentIdAndStatusNot(1L, WorkSession.WorkSessionStatus.COMPLETED))
                .thenReturn(Optional.empty());

        PayPeriodSummaryResponse response = payPeriodService.getPayPeriodSummaryResponse(1L, 1L);
        assertEquals(realPayPeriod.getPeriodStart(), response.periodStart());
        assertEquals(0, response.totalEarnedAmount().compareTo(BigDecimal.valueOf(10000)));
        assertEquals(0, response.totalEwaAmount().compareTo(BigDecimal.valueOf(1000)));
        assertEquals(0, response.remainingEwaLimit().compareTo(BigDecimal.valueOf(2000)));
        assertNull(response.activeSessionStatus());
    }

    @Test
    void summary_현재_세션_있을_때_currentEarned_반영() {
        when(employment.getWorkerId()).thenReturn(1L);
        when(employment.getId()).thenReturn(1L);
        when(employment.getWorker()).thenReturn(worker);
        when(worker.getName()).thenReturn("박사원");
        PayPeriod realPayPeriod = new PayPeriod(employment);
        realPayPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        realPayPeriod.addEwaAmount(BigDecimal.valueOf(1000));
        when(payPeriodRepository.findByEmployment_IdAndStatus(1L, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(realPayPeriod));
        when(workSessionRepository.findByEmploymentIdAndStatusNot(1L, WorkSession.WorkSessionStatus.COMPLETED))
                .thenReturn(Optional.of(workSession));
        when(workSession.getCurrentEarnedAmount()).thenReturn(BigDecimal.valueOf(5000));
        when(workSession.getStatus()).thenReturn(WorkSession.WorkSessionStatus.WORKING);

        PayPeriodSummaryResponse response = payPeriodService.getPayPeriodSummaryResponse(1L, 1L);
        assertEquals(0, response.totalEarnedAmount().compareTo(BigDecimal.valueOf(15000)));
        assertEquals(0, response.totalEwaAmount().compareTo(BigDecimal.valueOf(1000)));
        assertEquals(0, response.remainingEwaLimit().compareTo(BigDecimal.valueOf(3500)));
        assertEquals(WorkSession.WorkSessionStatus.WORKING, response.activeSessionStatus());
    }

    @Test
    void getPayPeriodSummaries_JDBC_레포지토리_위임() {
        PayPeriodSummaryResponse summaryResponse = new PayPeriodSummaryResponse(
                1L, "박사원", LocalDate.now(),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(1000),
                BigDecimal.valueOf(2000), null);
        when(payPeriodSummaryRepository.getSummaries(1L, 2L)).thenReturn(List.of(summaryResponse));

        List<PayPeriodSummaryResponse> result = payPeriodService.getPayPeriodSummaries(1L, 2L);

        assertEquals(1, result.size());
        assertEquals("박사원", result.getFirst().workerName());
    }
}
