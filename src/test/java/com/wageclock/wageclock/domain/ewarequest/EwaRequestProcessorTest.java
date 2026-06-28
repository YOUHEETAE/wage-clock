package com.wageclock.wageclock.domain.ewarequest;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EwaRequestProcessorTest {
    @Mock
    EwaRequestRepository ewaRequestRepository;
    @Mock
    Employment employment;
    @Mock
    EwaRequest ewaRequest;
    @InjectMocks
    EwaRequestProcessor ewaRequestProcessor;
    @Mock
    PayPeriodRepository payPeriodRepository;
    @Mock
    WorkSessionRepository workSessionRepository;

    @Test
    void 한도_초과_요청_시_예외() {
        when(employment.getWorkerId()).thenReturn(1L);
        PayPeriod payPeriod = new PayPeriod(employment);
        payPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        when(payPeriodRepository.findByEmploymentAndStatusWithLock(
                1L, PayPeriod.PayPeriodStatus.ACTIVE)).thenReturn(Optional.of(payPeriod));
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.valueOf(10000), "key-1");
        assertThrows(IllegalArgumentException.class, () -> ewaRequestProcessor.processEwaRequest(ewaRequestDto, 1L));
    }

    @Test
    void 멱등성_키_중복_요청_예외() {
        when(employment.getWorkerId()).thenReturn(1L);
        PayPeriod payPeriod = new PayPeriod(employment);
        payPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        when(payPeriodRepository.findByEmploymentAndStatusWithLock(
                1L, PayPeriod.PayPeriodStatus.ACTIVE)).thenReturn(Optional.of(payPeriod));
        when(ewaRequestRepository.existsByIdempotencyKey("key-1")).thenReturn(true);
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.ONE, "key-1");
        assertThrows(IllegalArgumentException.class, () -> ewaRequestProcessor.processEwaRequest(ewaRequestDto, 1L));
    }

    @Test
    void 정상_요청_시_응답_반환() {
        when(employment.getWorkerId()).thenReturn(1L);
        PayPeriod payPeriod = new PayPeriod(employment);
        payPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        when(payPeriodRepository.findByEmploymentAndStatusWithLock(
                1L, PayPeriod.PayPeriodStatus.ACTIVE)).thenReturn(Optional.of(payPeriod));
        when(workSessionRepository.findByEmploymentIdAndStatus(1L, WorkSession.WorkSessionStatus.PAUSED))
                .thenReturn(Optional.empty());
        when(ewaRequestRepository.save(any())).thenReturn(ewaRequest);
        when(ewaRequest.getId()).thenReturn(1L);
        when(ewaRequest.getRequestedAmount()).thenReturn(BigDecimal.ONE);
        when(ewaRequest.getStatus()).thenReturn(EwaRequest.EwaRequestStatus.PENDING);
        EwaRequestDto ewaRequestDto = new EwaRequestDto(1L, BigDecimal.ONE, "key-1");
        EwaResponseDto ewaResponseDto = new EwaResponseDto(1L, BigDecimal.ONE, EwaRequest.EwaRequestStatus.PENDING);
        assertEquals(ewaResponseDto, ewaRequestProcessor.processEwaRequest(ewaRequestDto, 1L));
    }
    @Test
    void EWA요청_없음_예외() {
        when(ewaRequestRepository.findByIdWithLock(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> ewaRequestProcessor.validateAndLockEwa(1L, 1L));
    }

    @Test
    void PENDING_아님_예외() {
        when(ewaRequestRepository.findByIdWithLock(1L)).thenReturn(Optional.of(ewaRequest));
        when(ewaRequest.getStatus()).thenReturn(EwaRequest.EwaRequestStatus.APPROVED);
        assertThrows(IllegalStateException.class, () -> ewaRequestProcessor.validateAndLockEwa(1L, 1L));
    }

    @Test
    void 다른_고용주_접근_예외() {
        when(ewaRequestRepository.findByIdWithLock(1L)).thenReturn(Optional.of(ewaRequest));
        when(ewaRequest.getStatus()).thenReturn(EwaRequest.EwaRequestStatus.PENDING);
        when(ewaRequest.getEmployerId()).thenReturn(2L);
        assertThrows(RuntimeException.class, () -> ewaRequestProcessor.validateAndLockEwa(1L, 1L));
    }

}
