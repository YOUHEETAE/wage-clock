package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class EwaRequestServiceTest {
    @Mock EwaRequestRepository ewaRequestRepository;
    @Mock RedissonClient redissonClient;
    @Mock RLock lock;
    @Mock Employment employment;
    @Mock Worker worker;
    @Mock SecurityContext securityContext;
    @Mock Authentication authentication;
    @Mock WorkSessionRepository  workSessionRepository;
    @Mock EwaRequest ewaRequest;


    @InjectMocks
    EwaRequestService ewaRequestService;

    @BeforeEach
    public void setUp() throws InterruptedException {
        when(authentication.getPrincipal()).thenReturn(1L);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
    }

    @Test
    void 한도_초과_요청_시_예외(){
        when(employment.getHourlyWage()).thenReturn(BigDecimal.valueOf(10000));
        when(employment.getWorker()).thenReturn(worker);
        when(worker.getId()).thenReturn(1L);
        WorkSession workSession = WorkSession.builder()
                .clockIn(LocalDateTime.now().minusHours(2))
                .employment(employment).build();
        workSession.clockOut();
        when(workSessionRepository.findById(1L)).thenReturn(Optional.of(workSession));
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.valueOf(10000), "key-1");
        assertThrows(IllegalArgumentException.class, () -> ewaRequestService.requestEwa(ewaRequestDto));
    }

    @Test
    void 멱등성_키_중복_요청_예외(){
        when(employment.getHourlyWage()).thenReturn(BigDecimal.valueOf(10000));
        when(employment.getWorker()).thenReturn(worker);
        when(worker.getId()).thenReturn(1L);
        WorkSession workSession = WorkSession.builder()
                .clockIn(LocalDateTime.now().minusHours(2))
                .employment(employment).build();
        workSession.clockOut();
        when(workSessionRepository.findById(1L)).thenReturn(Optional.of(workSession));
        when(ewaRequestRepository.existsByIdempotencyKey("key-1")).thenReturn(true);
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.ONE, "key-1");
        assertThrows(IllegalArgumentException.class, () -> ewaRequestService.requestEwa(ewaRequestDto));
    }

    @Test
    void 정상_요청_시_응답_반환(){
        when(employment.getHourlyWage()).thenReturn(BigDecimal.valueOf(10000));
        when(employment.getWorker()).thenReturn(worker);
        when(worker.getId()).thenReturn(1L);
        when(ewaRequestRepository.save(any())).thenReturn(ewaRequest);
        when(ewaRequest.getId()).thenReturn(1L);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.ONE);
        when(ewaRequest.getStatus()).thenReturn(EwaRequest.EwaRequestStatus.PENDING);
        WorkSession workSession = WorkSession.builder()
                .clockIn(LocalDateTime.now().minusHours(2))
                .employment(employment).build();
        workSession.clockOut();
        when(workSessionRepository.findById(1L)).thenReturn(Optional.of(workSession));
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.ONE, "key-1");
        EwaResponseDto ewaResponseDto = new EwaResponseDto(1L, BigDecimal.ONE, EwaRequest.EwaRequestStatus.PENDING);
        assertEquals(ewaResponseDto, ewaRequestService.requestEwa(ewaRequestDto));
    }

    @Test
    void 락_점유_실패_시_예외() throws InterruptedException {
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.ONE, "key-1");
        assertThrows(RuntimeException.class, () -> ewaRequestService.requestEwa(ewaRequestDto));
    }


}
