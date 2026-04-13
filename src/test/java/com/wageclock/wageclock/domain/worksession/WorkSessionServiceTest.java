package com.wageclock.wageclock.domain.worksession;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private WorkSessionService workSessionService;

    @Test
    void clockIn_employment_없음_예외() {
        when(employmentRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> workSessionService.clockIn(new ClockInRequest(1L), 1L));
    }

    @Test
    void clockIn_다른_워커_접근_예외() {
        when(employmentRepository.findById(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(2L);
        assertThrows(UnauthorizedException.class,
                () -> workSessionService.clockIn(new ClockInRequest(1L), 1L));
    }

    @Test
    void clockIn_중복_세션_예외() {
        when(employmentRepository.findById(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(1L);
        when(workSessionRepository.existsByEmploymentIdAndStatus(1L, WorkSession.WorkSessionStatus.WORKING)).thenReturn(true);
        assertThrows(DuplicateException.class,
                () -> workSessionService.clockIn(new ClockInRequest(1L), 1L));
    }

    @Test
    void clockIn_정상_응답_반환() {
        LocalDateTime now = LocalDateTime.now();
        when(employmentRepository.findById(1L)).thenReturn(Optional.of(employment));
        when(employment.getWorkerId()).thenReturn(1L);
        when(workSessionRepository.existsByEmploymentIdAndStatus(1L, WorkSession.WorkSessionStatus.WORKING)).thenReturn(false);
        when(workSessionRepository.save(any())).thenReturn(savedWorkSession);
        when(savedWorkSession.getId()).thenReturn(10L);
        when(savedWorkSession.getClockIn()).thenReturn(now);

        ClockInResponse response = workSessionService.clockIn(new ClockInRequest(1L), 1L);

        assertEquals(10L, response.sessionId());
        assertEquals(now, response.clockIn());
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
        when(workSessionRepository.findById(1L)).thenReturn(Optional.of(savedWorkSession));
        when(savedWorkSession.getWorkerId()).thenReturn(1L);
        when(savedWorkSession.getClockOut()).thenReturn(clockOutTime);
        when(savedWorkSession.getEarnedAmount()).thenReturn(BigDecimal.valueOf(50000));

        ClockOutResponse response = workSessionService.clockOut(new ClockOutRequest(1L), 1L);

        assertEquals(clockOutTime, response.clockOut());
        assertEquals(BigDecimal.valueOf(50000), response.earnedAmount());
    }
}
