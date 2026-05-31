package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;


@Service
public class PayPeriodService {

    private final PayPeriodRepository payPeriodRepository;
    private final WorkSessionRepository workSessionRepository;

    public PayPeriodService(PayPeriodRepository payPeriodRepository,
                            WorkSessionRepository workSessionRepository) {
        this.payPeriodRepository = payPeriodRepository;
        this.workSessionRepository = workSessionRepository;
    }

    @Transactional
    public ClosePayPeriodResponse closePayPeriod(Long employmentId, Long employerId){
        PayPeriod payPeriod = payPeriodRepository
                .findByEmploymentIdAndStatus(employmentId, PayPeriod.PayPeriodStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("PayPeriod not found"));
        if(!payPeriod.getEmployerId().equals(employerId)){
            throw new UnauthorizedException("unauthorized");
        }
        if(workSessionRepository.existsByEmploymentIdAndStatus(employmentId,
                WorkSession.WorkSessionStatus.WORKING)){
            throw new IllegalStateException("Working workSession exists");
        }
        if(workSessionRepository.existsByEmploymentIdAndStatus(employmentId,
                WorkSession.WorkSessionStatus.PAUSED)){
            throw new IllegalStateException("Paused workSession exists");
        }
        payPeriod.close();
        payPeriodRepository.save(payPeriod);
        return new ClosePayPeriodResponse(payPeriod.getPeriodStart(), payPeriod.getPeriodEnd(),
                payPeriod.getTotalEarnedAmount(), payPeriod.getTotalEwaAmount(),
                payPeriod.getActualPayAmount());
    }
    @Transactional(readOnly = true)
    public PayPeriodSummaryResponse getPayPeriodSummaryResponse(Long employmentId ,Long callerId){
        PayPeriod payPeriod = payPeriodRepository.findByEmploymentIdAndStatus(employmentId, PayPeriod.PayPeriodStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Worker not found"));
        if(!payPeriod.getEmployerId().equals(callerId) && !payPeriod.getWorkerId().equals(callerId)){
            throw new UnauthorizedException("unauthorized");
        }
        BigDecimal currentEarned = workSessionRepository
                .findByEmploymentIdAndStatusNot(employmentId, WorkSession.WorkSessionStatus.COMPLETED)
                .map(WorkSession::getCurrentEarnedAmount)
                .orElse(BigDecimal.ZERO);
        return new PayPeriodSummaryResponse(payPeriod.getTotalEarnedAmount().add(currentEarned),
                payPeriod.getTotalEwaAmount(),
                payPeriod.getRemainingEwaLimitWith(currentEarned));
    }
}
