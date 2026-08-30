package com.wageclock.wageclock.domain.ewarequest;

import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import java.util.List;
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
        if (!payPeriod.hasRegisteredAccount()) {
            throw new IllegalStateException("계좌 정보를 먼저 등록해주세요");
        }
        BigDecimal currentEarned = workSessionRepository
                .findByEmploymentIdAndStatusNot(ewaRequestDto.employmentId(), WorkSession.WorkSessionStatus.COMPLETED)
                .map(WorkSession::getCurrentEarnedAmount)
                .orElse(BigDecimal.ZERO);

        BigDecimal limitEwaAmount = payPeriod.getRemainingEwaLimitWith(currentEarned);

        if (ewaRequestDto.requestAmount().compareTo(limitEwaAmount) > 0 ||
                ewaRequestDto.requestAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid request amount");
        }

        if (ewaRequestRepository.existsByPayPeriodAndStatusIn(payPeriod,
                List.of(EwaRequest.EwaRequestStatus.PENDING, EwaRequest.EwaRequestStatus.PROCESSING))) {
            throw new IllegalStateException("이미 처리 중인 선지급 요청이 있습니다");
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
    public EwaResponseDto validateAndRejectEwa(Long ewaRequestId, Long employerId){
        EwaRequest ewaRequest = lockEwa(ewaRequestId);
        // 검증보다 먼저 잠근다. validateEwa의 getEmployerId()가 PayPeriod 프록시를 초기화하는데,
        // 그 뒤에 락을 잡으면 영속성 컨텍스트가 이미 들고 있는 값을 돌려주므로
        // 락 획득 전의 금액에서 차감하게 된다.
        PayPeriod payPeriod = payPeriodRepository.findByIdWithLock(ewaRequest.getPayPeriodId())
                .orElseThrow(() -> new NotFoundException("PayPeriod Not Found"));
        validateEwa(ewaRequest, employerId);
        ewaRequest.rejected();
        payPeriod.subtractEwaAmount(ewaRequest.getRequestedAmount());
        return new EwaResponseDto(ewaRequest.getId(), ewaRequest.getRequestedAmount(), ewaRequest.getStatus());
    }

    @Transactional
    public EwaRequest validateAndMarkProcessing(Long ewaRequestId, Long employerId){
        EwaRequest ewaRequest = lockEwa(ewaRequestId);
        validateEwa(ewaRequest, employerId);
        ewaRequest.processing();
        return ewaRequest;
    }

    private EwaRequest lockEwa(Long ewaRequestId){
        return ewaRequestRepository.findByIdWithLock(ewaRequestId)
                .orElseThrow(() -> new NotFoundException("Invalid request Id"));
    }

    private void validateEwa(EwaRequest ewaRequest, Long employerId){
        if(ewaRequest.getStatus() != EwaRequest.EwaRequestStatus.PENDING){
            throw new IllegalStateException("EWA request is not in PENDING status");
        }
        if(!ewaRequest.getEmployerId().equals(employerId)){
            throw new UnauthorizedException("Invalid employer Id");
        }
    }
}
