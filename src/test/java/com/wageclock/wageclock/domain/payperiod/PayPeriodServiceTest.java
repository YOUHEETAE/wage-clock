package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
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
    private EmploymentRepository employmentRepository;
    @Mock
    Employment employment;
    @Mock
    PayPeriod payPeriod;

    @Test
    void employment_없을_시_예외(){
        when(employmentRepository.findByIdAndEmployerId(1L, 1L))
                .thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> payPeriodService.closePayPeriod(1L, 1L));
    }
    @Test
    void ACTIVE_payPeriod_없을_시_예외(){
        when(employmentRepository.findByIdAndEmployerId(1L, 1L))
                .thenReturn(Optional.of(employment));
        when(payPeriodRepository.findByEmploymentAndStatus(employment, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> payPeriodService.closePayPeriod(1L, 1L));
    }
    @Test
    void workSession_WORKING_상태_존재_시_예외(){
        when(employmentRepository.findByIdAndEmployerId(1L, 1L))
                .thenReturn(Optional.of(employment));
        when(payPeriodRepository.findByEmploymentAndStatus(employment, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(payPeriod));
        when(workSessionRepository.existsByEmploymentIdAndStatus(1L,
                WorkSession.WorkSessionStatus.WORKING))
                .thenReturn(true);
        assertThrows(IllegalStateException.class, () -> payPeriodService.closePayPeriod(1L, 1L));
    }
    @Test
    void workSession_PAUSED_상태_존재_시_예외(){
        when(employmentRepository.findByIdAndEmployerId(1L, 1L))
                .thenReturn(Optional.of(employment));
        when(payPeriodRepository.findByEmploymentAndStatus(employment, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(payPeriod));
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
        when(employmentRepository.findByIdAndEmployerId(1L, 1L))
                .thenReturn(Optional.of(employment));
        when(workSessionRepository.existsByEmploymentIdAndStatus(1L,
                WorkSession.WorkSessionStatus.WORKING))
                .thenReturn(false);
        when(workSessionRepository.existsByEmploymentIdAndStatus(1L,
                WorkSession.WorkSessionStatus.PAUSED))
                .thenReturn(false);
        PayPeriod payPeriod = new PayPeriod(employment);
        payPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        payPeriod.addEwaAmount(BigDecimal.valueOf(1000));
        when(payPeriodRepository.findByEmploymentAndStatus(employment, PayPeriod.PayPeriodStatus.ACTIVE))
                .thenReturn(Optional.of(payPeriod));
        payPeriodService.closePayPeriod(1L, 1L);
        assertEquals(PayPeriod.PayPeriodStatus.CLOSED, payPeriod.getStatus());
        assertEquals(LocalDate.now(), payPeriod.getPeriodEnd());
        assertEquals(0, payPeriod.getActualPayAmount().compareTo(BigDecimal.valueOf(9000)));
    }
}
