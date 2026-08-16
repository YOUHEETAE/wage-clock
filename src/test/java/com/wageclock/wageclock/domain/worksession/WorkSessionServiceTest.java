package com.wageclock.wageclock.domain.worksession;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkSessionServiceTest {

    @Mock
    private WorkSessionRepository workSessionRepository;
    @Mock
    private EmploymentRepository employmentRepository;
    @Mock
    private Employment employment;
    @Mock
    private WorkSession savedWorkSession;
    @Mock
    PayPeriodRepository payPeriodRepository;
    @Mock
    PayPeriod payPeriod;

    @InjectMocks
    private WorkSessionService workSessionService;


    @Test
    void clockIn_employment_없음_예외() {
        when(employmentRepository.findByIdWithLock(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> workSessionService.clockIn(new ClockInRequest(1L), 1L));
    }

    @Test
    void clockIn_다른_워커_접근_예외() {
        when(employmentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(2L);
        assertThrows(UnauthorizedException.class,
                () -> workSessionService.clockIn(new ClockInRequest(1L), 1L));
    }

    @Test
    void clockIn_중복_세션_예외() {
        when(employmentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(1L);
        when(workSessionRepository.existsByEmploymentIdAndStatusNot(1L, WorkSession.WorkSessionStatus.COMPLETED)).thenReturn(true);
        assertThrows(DuplicateException.class,
                () -> workSessionService.clockIn(new ClockInRequest(1L), 1L));
    }

    @Test
    void clockIn_정상_응답_반환() {
        LocalDateTime now = LocalDateTime.now();
        when(payPeriodRepository.findByEmployment_IdAndStatusIn(any(), any())).thenReturn(Optional.of(payPeriod));
        when(employmentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(1L);
        when(workSessionRepository.existsByEmploymentIdAndStatusNot(1L, WorkSession.WorkSessionStatus.COMPLETED)).thenReturn(false);
        when(workSessionRepository.save(any())).thenReturn(savedWorkSession);
        when(savedWorkSession.getId()).thenReturn(10L);
        when(savedWorkSession.getClockIn()).thenReturn(now);
        when(savedWorkSession.getHourlyWage()).thenReturn(java.math.BigDecimal.valueOf(10000));

        ClockInResponse response = workSessionService.clockIn(new ClockInRequest(1L), 1L);

        assertEquals(10L, response.sessionId());
        assertEquals(now, response.clockIn());
        assertEquals(0, response.hourlyWage().compareTo(java.math.BigDecimal.valueOf(10000)));
    }

    // 정산 중에 출근하면 세션이 SETTLING인 PayPeriod에 붙고, 마감 후 퇴근한 적립액이 고아가 된다
    @Test
    void clockIn_정산_진행중이면_예외() {
        when(employmentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(1L);
        when(workSessionRepository.existsByEmploymentIdAndStatusNot(1L,
                WorkSession.WorkSessionStatus.COMPLETED)).thenReturn(false);
        when(payPeriodRepository.findByEmployment_IdAndStatusIn(any(), any())).thenReturn(Optional.of(payPeriod));
        when(payPeriod.getStatus()).thenReturn(PayPeriod.PayPeriodStatus.SETTLING);

        assertThrows(IllegalStateException.class,
                () -> workSessionService.clockIn(new ClockInRequest(1L), 1L));

        verify(workSessionRepository, never()).save(any());
        verify(payPeriodRepository, never()).save(any());
    }

    @Test
    void clockOut_세션_없음_예외() {
        when(workSessionRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> workSessionService.clockOut(new ClockOutRequest(1L), 1L));
    }

    @Test
    void clockOut_다른_워커_접근_예외() {
        when(workSessionRepository.findById(1L)).thenReturn(Optional.of(savedWorkSession));
        when(savedWorkSession.getWorkerId()).thenReturn(2L);
        assertThrows(UnauthorizedException.class,
                () -> workSessionService.clockOut(new ClockOutRequest(1L), 1L));
    }

    @Test
    void clockOut_정상_응답_반환() {
        LocalDateTime clockOutTime = LocalDateTime.now();
        when(savedWorkSession.getPayPeriod()).thenReturn(payPeriod);
        when(workSessionRepository.findById(1L)).thenReturn(Optional.of(savedWorkSession));
        when(savedWorkSession.getWorkerId()).thenReturn(1L);
        when(savedWorkSession.getClockOut()).thenReturn(clockOutTime);
        when(savedWorkSession.getEarnedAmount()).thenReturn(BigDecimal.valueOf(50000));

        ClockOutResponse response = workSessionService.clockOut(new ClockOutRequest(1L), 1L);

        assertEquals(clockOutTime, response.clockOut());
        assertEquals(BigDecimal.valueOf(50000), response.earnedAmount());
    }
    @Test
    void clockIn_PayPeriod_없으면_새로_생성() {
        when(employmentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(1L);
        when(workSessionRepository.existsByEmploymentIdAndStatusNot(1L, WorkSession.WorkSessionStatus.COMPLETED)).thenReturn(false);
        when(payPeriodRepository.findByEmployment_IdAndStatusIn(any(), any())).thenReturn(Optional.empty());
        when(payPeriodRepository.save(any())).thenReturn(payPeriod);
        when(workSessionRepository.save(any())).thenReturn(savedWorkSession);
        when(savedWorkSession.getId()).thenReturn(10L);
        when(savedWorkSession.getClockIn()).thenReturn(LocalDateTime.now());

        workSessionService.clockIn(new ClockInRequest(1L), 1L);

        verify(payPeriodRepository, times(1)).save(any(PayPeriod.class));
    }
    @Test
    void clockOut_PayPeriod_earnedAmount_업데이트() {
        when(workSessionRepository.findById(1L)).thenReturn(Optional.of(savedWorkSession));
        when(savedWorkSession.getWorkerId()).thenReturn(1L);
        when(savedWorkSession.getPayPeriod()).thenReturn(payPeriod);
        when(savedWorkSession.getEarnedAmount()).thenReturn(BigDecimal.valueOf(50000));

        workSessionService.clockOut(new ClockOutRequest(1L), 1L);

        verify(payPeriod).addEarnedAmount(BigDecimal.valueOf(50000));
    }
    @Test
    void pause_정상_처리() {
        when(workSessionRepository.findById(1L)).thenReturn(Optional.of(savedWorkSession));
        when(savedWorkSession.getWorkerId()).thenReturn(1L);

        workSessionService.pause(1L, 1L);

        verify(savedWorkSession).pause();
    }

    @Test
    void pause_다른_워커_접근_예외() {
        when(workSessionRepository.findById(1L)).thenReturn(Optional.of(savedWorkSession));
        when(savedWorkSession.getWorkerId()).thenReturn(2L);

        assertThrows(UnauthorizedException.class, () -> workSessionService.pause(1L, 1L));
    }

    @Test
    void resume_정상_처리() {
        when(workSessionRepository.findById(1L)).thenReturn(Optional.of(savedWorkSession));
        when(savedWorkSession.getWorkerId()).thenReturn(1L);

        workSessionService.resume(1L, 1L);

        verify(savedWorkSession).resume();
    }

    @Test
    void getCurrentSession_employment_없음_예외() {
        when(employmentRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> workSessionService.getCurrentSession(1L, 1L));
    }

    @Test
    void getCurrentSession_다른_워커_접근_예외() {
        when(employmentRepository.findById(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(2L);
        assertThrows(UnauthorizedException.class,
                () -> workSessionService.getCurrentSession(1L, 1L));
    }

    @Test
    void getCurrentSession_세션_없으면_빈_Optional() {
        when(employmentRepository.findById(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(1L);
        when(workSessionRepository.findByEmploymentIdAndStatusNot(1L, WorkSession.WorkSessionStatus.COMPLETED))
                .thenReturn(Optional.empty());

        Optional<CurrentSessionResponse> result = workSessionService.getCurrentSession(1L, 1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentSession_WORKING_세션_반환() {
        when(employmentRepository.findById(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(1L);
        when(savedWorkSession.getId()).thenReturn(10L);
        when(savedWorkSession.getStatus()).thenReturn(WorkSession.WorkSessionStatus.WORKING);
        when(workSessionRepository.findByEmploymentIdAndStatusNot(1L, WorkSession.WorkSessionStatus.COMPLETED))
                .thenReturn(Optional.of(savedWorkSession));

        CurrentSessionResponse result = workSessionService.getCurrentSession(1L, 1L).get();

        assertEquals(10L, result.sessionId());
        assertEquals(WorkSession.WorkSessionStatus.WORKING, result.status());
    }

    @Test
    void getCurrentSession_PAUSED_세션_반환() {
        when(employmentRepository.findById(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(1L);
        when(savedWorkSession.getId()).thenReturn(10L);
        when(savedWorkSession.getStatus()).thenReturn(WorkSession.WorkSessionStatus.PAUSED);
        when(workSessionRepository.findByEmploymentIdAndStatusNot(1L, WorkSession.WorkSessionStatus.COMPLETED))
                .thenReturn(Optional.of(savedWorkSession));

        CurrentSessionResponse result = workSessionService.getCurrentSession(1L, 1L).get();

        assertEquals(10L, result.sessionId());
        assertEquals(WorkSession.WorkSessionStatus.PAUSED, result.status());
    }
}
