package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;


@Service
public class PayPeriodService {

    private final PayPeriodRepository payPeriodRepository;
    private final WorkSessionRepository workSessionRepository;
    private final PayPeriodSummaryRepository payPeriodSummaryRepository;

    public PayPeriodService(PayPeriodRepository payPeriodRepository,
                            WorkSessionRepository workSessionRepository,
                            PayPeriodSummaryRepository payPeriodSummaryRepository) {
        this.payPeriodRepository = payPeriodRepository;
        this.workSessionRepository = workSessionRepository;
        this.payPeriodSummaryRepository = payPeriodSummaryRepository;
    }

    @Transactional(readOnly = true)
    public PayPeriodSummaryResponse getPayPeriodSummaryResponse(Long employmentId, Long workerId){
        PayPeriod payPeriod = payPeriodRepository.findByEmployment_IdAndStatusIn(employmentId,
                        List.of(PayPeriod.PayPeriodStatus.ACTIVE, PayPeriod.PayPeriodStatus.SETTLING))
                .orElseThrow(() -> new NotFoundException("PayPeriod not found"));
        if (!payPeriod.getWorkerId().equals(workerId)) throw new UnauthorizedException("unauthorized");
        java.util.Optional<WorkSession> activeSession = workSessionRepository
                .findByEmploymentIdAndStatusNot(employmentId, WorkSession.WorkSessionStatus.COMPLETED);
        BigDecimal currentEarned = activeSession.map(WorkSession::getCurrentEarnedAmount).orElse(BigDecimal.ZERO);
        return toSummaryResponse(payPeriod, currentEarned, activeSession.map(WorkSession::getStatus).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<PayPeriodSummaryResponse> getPayPeriodSummaries(Long workplaceId, Long employerId) {
        return payPeriodSummaryRepository.getSummaries(workplaceId, employerId);
    }

    private PayPeriodSummaryResponse toSummaryResponse(PayPeriod payPeriod, BigDecimal currentEarned,
                                                        WorkSession.WorkSessionStatus activeSessionStatus) {
        return new PayPeriodSummaryResponse(
                payPeriod.getEmploymentId(),
                payPeriod.getWorkerName(),
                payPeriod.getPeriodStart(),
                payPeriod.getTotalEarnedAmount().add(currentEarned),
                payPeriod.getTotalEwaAmount(),
                payPeriod.getRemainingEwaLimitWith(currentEarned),
                activeSessionStatus,
                payPeriod.getStatus());
    }
}
