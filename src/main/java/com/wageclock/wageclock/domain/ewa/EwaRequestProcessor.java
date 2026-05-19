package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// Spring 프록시 self-invocation 우회용 분리 클래스
@Service
public class EwaRequestProcessor {

    private final WorkSessionRepository workSessionRepository;
    private final EwaRequestRepository ewaRequestRepository;

    public EwaRequestProcessor(WorkSessionRepository workSessionRepository,
                               EwaRequestRepository ewaRequestRepository) {
        this.workSessionRepository = workSessionRepository;
        this.ewaRequestRepository = ewaRequestRepository;
    }

    @Transactional
    public EwaResponseDto processEwaRequest(EwaRequestDto ewaRequestDto, Long workerId){
        WorkSession workSession = workSessionRepository.findByIdWithLock(ewaRequestDto.sessionId())
                .orElseThrow(() -> new NotFoundException("Invalid session Id"));
        if (!workSession.getWorkerId().equals(workerId)) {
            throw new UnauthorizedException("Invalid worker Id");
        }
        BigDecimal limitEwaAmount = workSession.getRemainingEwaLimit();

        if (ewaRequestDto.requestAmount().compareTo(limitEwaAmount) > 0 ||
                ewaRequestDto.requestAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid request amount");
        }

        if (ewaRequestRepository.existsByIdempotencyKey(ewaRequestDto.idempotencyKey())) {
            throw new IllegalArgumentException("Duplicate idempotency key");
        }

        EwaRequest ewaRequest = ewaRequestRepository.save(EwaRequest.builder()
                .workSession(workSession)
                .requestedAmount(ewaRequestDto.requestAmount())
                .idempotencyKey(ewaRequestDto.idempotencyKey())
                .build());

        workSession.addEwaAmount(ewaRequestDto.requestAmount());
        workSessionRepository.save(workSession);

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
}
