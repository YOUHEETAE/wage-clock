package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.payment.PaymentResult;
import com.wageclock.wageclock.domain.payment.PaymentService;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.TooManyRequestsException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Service
public class EwaRequestService {

    private final WorkSessionRepository workSessionRepository;
    private final EwaRequestRepository ewaRequestRepository;
    private final RedissonClient redissonClient;
    private final PaymentService paymentService;

    public EwaRequestService(WorkSessionRepository workSessionRepository,
                             EwaRequestRepository ewaRequestRepository,
                             RedissonClient redissonClient, PaymentService paymentService) {
        this.workSessionRepository = workSessionRepository;
        this.ewaRequestRepository = ewaRequestRepository;
        this.redissonClient = redissonClient;
        this.paymentService = paymentService;
    }

    public EwaResponseDto requestEwa(EwaRequestDto ewaRequestDto, Long workerId){
        RLock lock = redissonClient.getLock("ewa:lock:" + workerId);
        try {
            if(!lock.tryLock(3,5, TimeUnit.SECONDS)){
                throw new TooManyRequestsException("Too many Request");
            }
            return processEwaRequest(ewaRequestDto, workerId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock Interrupted");
        } finally {
            if(lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private EwaResponseDto processEwaRequest(EwaRequestDto ewaRequestDto, Long workerId){
        WorkSession workSession = workSessionRepository.findById(ewaRequestDto.sessionId())
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

    public EwaResponseDto approveEwa(Long ewaRequestId, Long employerId){
        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaRequestId)
                .orElseThrow(() -> new NotFoundException("Invalid request Id"));
        if(ewaRequest.getStatus() != EwaRequest.EwaRequestStatus.PENDING){
            throw new IllegalStateException("EWA request is not in PENDING status");
        }
        if(!ewaRequest.getEmployerId().equals(employerId)){
            throw new UnauthorizedException("Invalid employer Id");
        }
        PaymentResult status = paymentService.processPayment(ewaRequest.getRequestedAmount());
        if(status.equals(PaymentResult.SUCCESS)){
            ewaRequest.approved();
            ewaRequestRepository.save(ewaRequest);
        } else if (status.equals(PaymentResult.FAILURE)) {
            ewaRequest.rejected();
            ewaRequest.getWorkSession().subtractEwaAmount(ewaRequest.getRequestedAmount());
            workSessionRepository.save(ewaRequest.getWorkSession());
            ewaRequestRepository.save(ewaRequest);
        }
        return new EwaResponseDto(ewaRequestId, ewaRequest.getRequestedAmount(), ewaRequest.getStatus());
    }

    public EwaResponseDto rejectEwa(Long ewaRequestId, Long employerId){
        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaRequestId)
                .orElseThrow(() -> new NotFoundException("Invalid request Id"));
        if(ewaRequest.getStatus() != EwaRequest.EwaRequestStatus.PENDING){
            throw new IllegalStateException("EWA request is not in PENDING status");
        }
        if(!ewaRequest.getEmployerId().equals(employerId)){
            throw new UnauthorizedException("Invalid employer Id");
        }
        ewaRequest.rejected();
        ewaRequestRepository.save(ewaRequest);
        ewaRequest.getWorkSession().subtractEwaAmount(ewaRequest.getRequestedAmount());
        workSessionRepository.save(ewaRequest.getWorkSession());

        return new EwaResponseDto(ewaRequestId, ewaRequest.getRequestedAmount(), ewaRequest.getStatus());
    }
}