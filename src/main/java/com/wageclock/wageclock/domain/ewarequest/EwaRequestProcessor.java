package com.wageclock.wageclock.domain.ewarequest;

import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// Spring 프록시 self-invocation 우회용 분리 클래스
@Component
public class EwaRequestProcessor {

    private final EwaRequestRepository ewaRequestRepository;
    private final PayPeriodRepository payPeriodRepository;
    private final WorkSessionRepository workSessionRepository;

    public EwaRequestProcessor(EwaRequestRepository ewaRequestRepository,
                               PayPeriodRepository payPeriodRepository,
                               WorkSessionRepository workSessionRepository) {
        this.ewaRequestRepository = ewaRequestRepository;
        this.payPeriodRepository = payPeriodRepository;
        this.workSessionRepository = workSessionRepository;
    }

    @Transactional
    public EwaResponseDto processEwaRequest(EwaRequestDto ewaRequestDto, Long workerId){
        PayPeriod payPeriod = payPeriodRepository.findByEmploymentAndStatusWithLock(ewaRequestDto.employmentId(),
                PayPeriod.PayPeriodStatus.ACTIVE).orElseThrow(() -> new NotFoundException("Pay Period Not Found"));
        if (!payPeriod.getWorkerId().equals(workerId)) {
            throw new UnauthorizedException("Invalid worker Id");
        }
        BigDecimal pausedEarnedAmount = workSessionRepository
                .findByEmploymentIdAndStatus(ewaRequestDto.employmentId(), WorkSession.WorkSessionStatus.PAUSED)
                .map(WorkSession::getEarnedAmount)
                .orElse(BigDecimal.ZERO);

        BigDecimal limitEwaAmount = payPeriod.getRemainingEwaLimitWith(pausedEarnedAmount);

        if (ewaRequestDto.requestAmount().compareTo(limitEwaAmount) > 0 ||
                ewaRequestDto.requestAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid request amount");
        }

        if (ewaRequestRepository.existsByIdempotencyKey(ewaRequestDto.idempotencyKey())) {
            throw new IllegalArgumentException("Duplicate idempotency key");
        }

        EwaRequest ewaRequest = ewaRequestRepository.save(EwaRequest.builder()
                .payPeriod(payPeriod)
                .requestedAmount(ewaRequestDto.requestAmount())
                .idempotencyKey(ewaRequestDto.idempotencyKey())
                .build());

        payPeriod.addEwaAmount(ewaRequestDto.requestAmount());

        return new EwaResponseDto(ewaRequest.getId(),
                ewaRequest.getRequestedAmount(), ewaRequest.getStatus());
    }
    @Transactional
    public EwaRequest validateAndLockEwa(Long ewaRequestId, Long employerId){
        EwaRequest ewaRequest = ewaRequestRepository.findByIdWithLock(ewaRequestId)
                .orElseThrow(() -> new NotFoundException("Invalid request Id"));
        if(ewaRequest.getStatus() != EwaRequest.EwaRequestStatus.PENDING){
            throw new IllegalStateException("EWA request is not in PENDING status");
        }
        if(!ewaRequest.getEmployerId().equals(employerId)){
            throw new UnauthorizedException("Invalid employer Id");
        }
        return ewaRequest;
    }
    @Transactional
    public void processRejectEwa(EwaRequest ewaRequest){
        ewaRequest.rejected();
        ewaRequestRepository.save(ewaRequest);
        ewaRequest.getPayPeriod().subtractEwaAmount(ewaRequest.getRequestedAmount());
        payPeriodRepository.save(ewaRequest.getPayPeriod());
    }
}
