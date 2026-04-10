package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class EwaRequestService {

    private final WorkSessionRepository workSessionRepository;
    private final EwaRequestRepository ewaRequestRepository;

    public EwaRequestService(WorkSessionRepository workSessionRepository,
                             EwaRequestRepository ewaRequestRepository) {
        this.workSessionRepository = workSessionRepository;
        this.ewaRequestRepository = ewaRequestRepository;
    }

    public EwaResponseDto requestEwa(EwaRequestDto ewaRequestDto){
        Long workerId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        WorkSession workSession = workSessionRepository.findById(ewaRequestDto.sessionId())
                .orElseThrow(()-> new IllegalArgumentException("Invalid session Id"));
        if(!workSession.getEmployment().getWorker().getId().equals(workerId)){
            throw new IllegalArgumentException("Invalid worker Id");
        }

        if(workSession.getStatus() != WorkSession.WorkSessionStatus.COMPLETED){
            throw new IllegalArgumentException("Work session status is not COMPLETED");
        }

        BigDecimal limitAmount = workSession.getEarnedAmount().multiply(BigDecimal.valueOf(0.3));

        if(ewaRequestDto.requestAmount().compareTo(limitAmount) > 0 ||
        ewaRequestDto.requestAmount().compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Invalid request amount");
        }

        if(ewaRequestRepository.existsByIdempotencyKey(ewaRequestDto.idempotencyKey())){
            throw new IllegalArgumentException("Duplicate idempotency key");
        }

        EwaRequest ewaRequest = ewaRequestRepository.save(EwaRequest.builder()
                .workSession(workSession)
                .requestedAmount(ewaRequestDto.requestAmount())
                .idempotencyKey(ewaRequestDto.idempotencyKey())
                .build());
        return new EwaResponseDto(ewaRequest.getId(),
                ewaRequest.getRequestedAmount(), ewaRequest.getStatus());
    }
}
