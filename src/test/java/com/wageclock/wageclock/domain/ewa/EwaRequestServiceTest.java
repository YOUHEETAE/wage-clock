package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.payment.Payment;
import com.wageclock.wageclock.domain.payment.PortOnePaymentService;
import com.wageclock.wageclock.domain.payment.VirtualAccountResult;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

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
    @Mock WorkSessionRepository workSessionRepository;
    @Mock WorkSession workSession;
    @Mock PortOnePaymentService portOnePaymentService;
    @Mock EwaRequest ewaRequest;
    @Mock Payment payment;

    @InjectMocks
    EwaRequestService ewaRequestService;

    private void setUpLock() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
    }

    @Test
    void 한도_초과_요청_시_예외() throws InterruptedException {
        setUpLock();
        when(employment.getHourlyWage()).thenReturn(BigDecimal.valueOf(10000));
        when(employment.getWorker()).thenReturn(worker);
        when(worker.getId()).thenReturn(1L);
        WorkSession workSession = WorkSession.builder()
                .clockIn(LocalDateTime.now().minusHours(2))
                .employment(employment).build();
        workSession.clockOut();
        when(workSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(workSession));
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.valueOf(10000), "key-1");
        assertThrows(IllegalArgumentException.class, () -> ewaRequestService.requestEwa(ewaRequestDto, 1L));
    }

    @Test
    void 멱등성_키_중복_요청_예외() throws InterruptedException {
        setUpLock();
        when(employment.getHourlyWage()).thenReturn(BigDecimal.valueOf(10000));
        when(employment.getWorker()).thenReturn(worker);
        when(worker.getId()).thenReturn(1L);
        WorkSession workSession = WorkSession.builder()
                .clockIn(LocalDateTime.now().minusHours(2))
                .employment(employment).build();
        workSession.clockOut();
        when(workSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(workSession));
        when(ewaRequestRepository.existsByIdempotencyKey("key-1")).thenReturn(true);
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.ONE, "key-1");
        assertThrows(IllegalArgumentException.class, () -> ewaRequestService.requestEwa(ewaRequestDto, 1L));
    }

    @Test
    void 정상_요청_시_응답_반환() throws InterruptedException {
        setUpLock();
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
        when(workSessionRepository.findByIdWithLock(1L)).thenReturn(Optional.of(workSession));
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.ONE, "key-1");
        EwaResponseDto ewaResponseDto = new EwaResponseDto(1L, BigDecimal.ONE, EwaRequest.EwaRequestStatus.PENDING);
        assertEquals(ewaResponseDto, ewaRequestService.requestEwa(ewaRequestDto, 1L));
    }

    @Test
    void 락_점유_실패_시_예외() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.ONE, "key-1");
        assertThrows(RuntimeException.class, () -> ewaRequestService.requestEwa(ewaRequestDto, 1L));
    }

    @Test
    void EWA요청_없음_예외() {
        when(ewaRequestRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> ewaRequestService.initiateEwa(1L, 1L));
    }

    @Test
    void PENDING_아님_예외() {
        when(ewaRequestRepository.findById(1L)).thenReturn(Optional.of(ewaRequest));
        when(ewaRequest.getStatus()).thenReturn(EwaRequest.EwaRequestStatus.APPROVED);
        assertThrows(IllegalStateException.class, () -> ewaRequestService.initiateEwa(1L, 1L));
    }

    @Test
    void 다른_고용주_접근_예외() {
        when(ewaRequestRepository.findById(1L)).thenReturn(Optional.of(ewaRequest));
        when(ewaRequest.getStatus()).thenReturn(EwaRequest.EwaRequestStatus.PENDING);
        when(ewaRequest.getEmployerId()).thenReturn(2L);
        assertThrows(RuntimeException.class, () -> ewaRequestService.initiateEwa(1L, 1L));
    }

    @Test
    void 정상_승인() {
        when(ewaRequestRepository.findById(1L)).thenReturn(Optional.of(ewaRequest));
        when(ewaRequest.getStatus())
                .thenReturn(EwaRequest.EwaRequestStatus.PENDING);
        when(ewaRequest.getEmployerId()).thenReturn(1L);
        when(portOnePaymentService.processPayment(any())).thenReturn(payment);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(100));
        when(payment.getPortOnePaymentId()).thenReturn("test-id");
        when(portOnePaymentService.getAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("SHINHAN", "123456", "2026-05-07"));

        InitiateEwaResponse response = ewaRequestService.initiateEwa(1L, 1L);
        assertEquals(EwaRequest.EwaRequestStatus.PENDING, response.status());
    }

    @Test
    void 정상_거절() {
        when(ewaRequestRepository.findById(1L)).thenReturn(Optional.of(ewaRequest));
        when(ewaRequest.getStatus())
                .thenReturn(EwaRequest.EwaRequestStatus.PENDING)
                .thenReturn(EwaRequest.EwaRequestStatus.REJECTED);
        when(ewaRequest.getEmployerId()).thenReturn(1L);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.valueOf(100));
        when(ewaRequest.getWorkSession()).thenReturn(workSession);

        EwaResponseDto response = ewaRequestService.rejectEwa(1L, 1L);
        assertEquals(EwaRequest.EwaRequestStatus.REJECTED, response.status());
    }
}
