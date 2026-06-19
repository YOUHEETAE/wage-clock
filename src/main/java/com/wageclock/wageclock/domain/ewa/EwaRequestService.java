package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.EwaTransfer.EwaTransferService;
import com.wageclock.wageclock.global.exception.TooManyRequestsException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class EwaRequestService {

    private final RedissonClient redissonClient;
    private final EwaRequestProcessor  ewaRequestProcessor;
    private final EwaTransferService ewaTransferService;

    public EwaRequestService(RedissonClient redissonClient,
                             EwaRequestProcessor  ewaRequestProcessor, EwaTransferService ewaTransferService) {
        this.redissonClient = redissonClient;
        this.ewaRequestProcessor = ewaRequestProcessor;
        this.ewaTransferService = ewaTransferService;
    }


    public EwaResponseDto requestEwa(EwaRequestDto ewaRequestDto, Long workerId){
        RLock lock = redissonClient.getLock("ewa:lock:" + workerId);
        try {
            if(!lock.tryLock(3,5, TimeUnit.SECONDS)){
                throw new TooManyRequestsException("Too many Request");
            }
            return ewaRequestProcessor.processEwaRequest(ewaRequestDto, workerId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock Interrupted");
        } finally {
            if(lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public InitiateEwaResponse initiateEwa(Long ewaRequestId, Long employerId){
        EwaRequest ewaRequest = ewaRequestProcessor.validateAndLockEwa(ewaRequestId, employerId);
        EwaRequest.EwaRequestStatus status = ewaTransferService.processTransfer(ewaRequest);
        return new InitiateEwaResponse(ewaRequestId, ewaRequest.getRequestedAmount(), status);
    }

    @Transactional
    public EwaResponseDto rejectEwa(Long ewaRequestId, Long employerId){
        EwaRequest ewaRequest = ewaRequestProcessor.validateAndLockEwa(ewaRequestId, employerId);
        ewaRequestProcessor.processRejectEwa(ewaRequest);
        return new EwaResponseDto(ewaRequestId, ewaRequest.getRequestedAmount(), ewaRequest.getStatus());
    }
}