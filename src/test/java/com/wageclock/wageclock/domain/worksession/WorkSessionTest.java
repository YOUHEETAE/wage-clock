package com.wageclock.wageclock.domain.worksession;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WorkSessionTest {

    @Mock
    Employment employment;
    @Mock
    PayPeriod payPeriod;

    @BeforeEach
    public void setup() {
        when(employment.getHourlyWage()).thenReturn(BigDecimal.valueOf(10000));
    }

    @Test
    void 생성_직후_WORKING_상태(){
        WorkSession workSession = WorkSession.builder()
                .employment(employment)
                .clockIn(LocalDateTime.now())
                .build();

        assertFalse(workSession.isCompleted());
    }

    @Test
    void 완료후_completed_상태(){
        WorkSession workSession = WorkSession.builder()
                .employment(employment)
                .clockIn(LocalDateTime.now())
                .build();
        workSession.clockOut();
        assertTrue(workSession.isCompleted());
    }

    @Test
    void 완료된_세션_재퇴근시_예외(){
        WorkSession workSession = WorkSession.builder()
                .employment(employment)
                .clockIn(LocalDateTime.now())
                .build();
        workSession.clockOut();
        assertThrows(IllegalStateException.class, workSession::clockOut);
    }
    @Test
    void pause_시_PAUSED_상태(){
        WorkSession workSession = WorkSession.builder()
                .employment(employment)
                .clockIn(LocalDateTime.now())
                .build();
        workSession.pause();
        assertEquals(WorkSession.WorkSessionStatus.PAUSED, workSession.getStatus());
        assertTrue(workSession.isPaused());
    }
    @Test
    void resume_시_WORKING_상태(){
        WorkSession workSession = WorkSession.builder()
                .employment(employment)
                .clockIn(LocalDateTime.now())
                .build();
        workSession.resume();
        assertEquals(WorkSession.WorkSessionStatus.WORKING, workSession.getStatus());
        assertFalse(workSession.isPaused());
    }
    @Test
    void pause_후_earnedAmount_스냅샷_저장() throws InterruptedException {
        WorkSession workSession = WorkSession.builder()
                .employment(employment)
                .payPeriod(payPeriod)
                .clockIn(LocalDateTime.now())
                .build();

        Thread.sleep(1100);
        workSession.pause();

        assertTrue(workSession.getEarnedAmount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(WorkSession.WorkSessionStatus.PAUSED, workSession.getStatus());
    }

    @Test
    void resume_후_누적_검증() throws InterruptedException {
        WorkSession workSession = WorkSession.builder()
                .employment(employment)
                .payPeriod(payPeriod)
                .clockIn(LocalDateTime.now())
                .build();

        Thread.sleep(1100);
        workSession.pause();
        BigDecimal firstSnapshot = workSession.getEarnedAmount();

        workSession.resume();
        Thread.sleep(1100);
        workSession.pause();

        assertTrue(workSession.getEarnedAmount().compareTo(firstSnapshot) > 0);
    }

    @Test
    void paused_상태에서_getCurrentEarnedAmount_스냅샷_반환() throws InterruptedException {
        WorkSession workSession = WorkSession.builder()
                .employment(employment)
                .payPeriod(payPeriod)
                .clockIn(LocalDateTime.now())
                .build();
        Thread.sleep(100);
        workSession.pause();
        BigDecimal snapshot = workSession.getEarnedAmount();

        Thread.sleep(100);
        assertEquals(0, snapshot.compareTo(workSession.getCurrentEarnedAmount()));
    }
}
