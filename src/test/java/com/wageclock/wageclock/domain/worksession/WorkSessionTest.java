package com.wageclock.wageclock.domain.worksession;

import com.wageclock.wageclock.domain.employment.Employment;
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
    void 선지급_한도_계산(){
        WorkSession workSession = WorkSession.builder()
                .employment(employment)
                .clockIn(LocalDateTime.now().minusHours(2))
                .build();
        workSession.clockOut();
        BigDecimal ewaLimit = workSession.getRemainingEwaLimit();
        assertEquals(0, ewaLimit.compareTo(workSession.getEarnedAmount().multiply(BigDecimal.valueOf(0.3))));
    }

}
