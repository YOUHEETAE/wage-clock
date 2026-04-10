package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Service
public class EwaRequestService {

    private final WorkSessionRepository workSessionRepository;
    private final EwaRequestRepository ewaRequestRepository;
    private final RedissonClient redissonClient;

    public EwaRequestService(WorkSessionRepository workSessionRepository,
                             EwaRequestRepository ewaRequestRepository,
                             RedissonClient redissonClient) {
        this.workSessionRepository = workSessionRepository;
        this.ewaRequestRepository = ewaRequestRepository;
        this.redissonClient = redissonClient;
    }

    public EwaResponseDto requestEwa(EwaRequestDto ewaRequestDto){
        Long workerId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        RLock lock = redissonClient.getLock("ewa:lock:" + workerId);
        try {
            if(!lock.tryLock(3,5, TimeUnit.SECONDS)){
                throw new RuntimeException("Too many Request");
            }
            WorkSession workSession = workSessionRepository.findById(ewaRequestDto.sessionId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid session Id"));
            if (!workSession.getEmployment().getWorker().getId().equals(workerId)) {
                throw new IllegalArgumentException("Invalid worker Id");
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
            return new EwaResponseDto(ewaRequest.getId(),
                    ewaRequest.getRequestedAmount(), ewaRequest.getStatus());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock Interrupted");
        } finally {
            if(lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
