package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.employment.EmploymentRepository;
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
    private final EmploymentRepository employmentRepository;

    public PayPeriodService(PayPeriodRepository payPeriodRepository,
                            WorkSessionRepository workSessionRepository,
                            PayPeriodSummaryRepository payPeriodSummaryRepository, EmploymentRepository employmentRepository) {
        this.payPeriodRepository = payPeriodRepository;
        this.workSessionRepository = workSessionRepository;
        this.payPeriodSummaryRepository = payPeriodSummaryRepository;
        this.employmentRepository = employmentRepository;
    }

    @Transactional
    public ClosePayPeriodResponse closePayPeriod(Long employmentId, Long employerId){
        // 출근(clockIn)과 직렬화하기 위한 락. 반환값은 사용하지 않는다.
        // 락을 PayPeriod 조회보다 먼저 잡아야 대기 후 커밋된 상태를 읽는다.
        employmentRepository.findByIdWithLock(employmentId)
                .orElseThrow(() -> new NotFoundException("employment not found"));
        PayPeriod payPeriod = payPeriodRepository
                .findByEmployment_IdAndStatus(employmentId, PayPeriod.PayPeriodStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("PayPeriod not found"));
        if(!payPeriod.getEmployerId().equals(employerId)){
            throw new UnauthorizedException("unauthorized");
        }
        if(workSessionRepository.existsByEmploymentIdAndStatusNot(employmentId, WorkSession.WorkSessionStatus.COMPLETED)){
            throw new IllegalStateException("Active work session exists");
        }
        payPeriod.close();
        return new ClosePayPeriodResponse(payPeriod.getPeriodStart(), payPeriod.getPeriodEnd(),
                payPeriod.getTotalEarnedAmount(), payPeriod.getTotalEwaAmount(),
                payPeriod.getActualPayAmount());
    }
    @Transactional(readOnly = true)
    public PayPeriodSummaryResponse getPayPeriodSummaryResponse(Long employmentId, Long workerId){
        PayPeriod payPeriod = payPeriodRepository.findByEmployment_IdAndStatus(employmentId, PayPeriod.PayPeriodStatus.ACTIVE)
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

    @Transactional
    public List<ClosePayPeriodResponse> bulkClosePayPeriods(List<Long> employmentIds, Long employerId) {
        // 출근(clockIn)과 직렬화하기 위한 락. 반환값은 사용하지 않는다.
        employmentRepository.findAllByIdInWithLock(employmentIds);
        List<PayPeriod> payPeriods = payPeriodRepository
                .findAllByEmploymentIdInAndEmployerIdAndStatusWithLock(employmentIds, employerId);
        if (payPeriods.size() != employmentIds.size()) {
            throw new UnauthorizedException("unauthorized");
        }
        return payPeriods.stream()
                .map(pp -> {
                    Long employmentId = pp.getEmploymentId();
                    if(workSessionRepository.existsByEmploymentIdAndStatusNot(employmentId, WorkSession.WorkSessionStatus.COMPLETED)){
                        throw new IllegalStateException("Active workSession exists for employment: " + employmentId);
                    }
                    pp.close();
                    return new ClosePayPeriodResponse(pp.getPeriodStart(), pp.getPeriodEnd(),
                            pp.getTotalEarnedAmount(), pp.getTotalEwaAmount(), pp.getActualPayAmount());
                })
                .toList();
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
                activeSessionStatus);
    }
}
