package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;


@Service
public class PayPeriodService {

    private final PayPeriodRepository payPeriodRepository;
    private final EmploymentRepository employmentRepository;
    private final WorkSessionRepository workSessionRepository;

    public PayPeriodService(PayPeriodRepository payPeriodRepository,
                            EmploymentRepository employmentRepository,
                            WorkSessionRepository workSessionRepository) {
        this.payPeriodRepository = payPeriodRepository;
        this.employmentRepository = employmentRepository;
        this.workSessionRepository = workSessionRepository;
    }

    @Transactional
    public ClosePayPeriodResponse closePayPeriod(Long employmentId, Long employerId){
        Employment employment = employmentRepository.findByIdAndEmployerId(employmentId, employerId)
                .orElseThrow(() -> new NotFoundException("Employment not found"));
        PayPeriod payPeriod = payPeriodRepository
                .findByEmploymentAndStatus(employment, PayPeriod.PayPeriodStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("PayPeriod not found"));
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
}
