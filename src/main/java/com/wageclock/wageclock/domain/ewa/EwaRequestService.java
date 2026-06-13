package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.payment.*;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.TooManyRequestsException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class EwaRequestService {

    private final EwaRequestRepository ewaRequestRepository;
    private final RedissonClient redissonClient;
    private final PortOnePaymentService portOnePaymentService;
    private final EwaRequestProcessor  ewaRequestProcessor;
    private final PayPeriodRepository payPeriodRepository;

    public EwaRequestService(PayPeriodRepository payPeriodRepository,
                             EwaRequestRepository ewaRequestRepository, RedissonClient redissonClient,
                             PortOnePaymentService portOnePaymentService, EwaRequestProcessor  ewaRequestProcessor) {
        this.payPeriodRepository = payPeriodRepository;
        this.ewaRequestRepository = ewaRequestRepository;
        this.redissonClient = redissonClient;
        this.portOnePaymentService = portOnePaymentService;
        this.ewaRequestProcessor = ewaRequestProcessor;
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
        Payment payment = portOnePaymentService.processPayment(ewaRequest);
        VirtualAccountResult account = portOnePaymentService.getAccount(payment.getPortOnePaymentId(),
                ewaRequest.getRequestedAmount(), "EWA-" + ewaRequestId, ewaRequest.getEmployerName());
        portOnePaymentService.updatePayment(payment, account);
        return new InitiateEwaResponse(ewaRequestId, ewaRequest.getRequestedAmount(),
                ewaRequest.getStatus(), account.accountNumber(), account.bank(), account.expiredAt());
    }

    @Transactional
    public EwaResponseDto rejectEwa(Long ewaRequestId, Long employerId){
        EwaRequest ewaRequest = ewaRequestRepository.findByIdWithLock(ewaRequestId)
                .orElseThrow(() -> new NotFoundException("Invalid request Id"));
        if(ewaRequest.getStatus() != EwaRequest.EwaRequestStatus.PENDING){
            throw new IllegalStateException("EWA request is not in PENDING status");
        }
        if(!ewaRequest.getEmployerId().equals(employerId)){
            throw new UnauthorizedException("Invalid employer Id");
        }
        ewaRequest.rejected();
        ewaRequestRepository.save(ewaRequest);
        ewaRequest.getPayPeriod().subtractEwaAmount(ewaRequest.getRequestedAmount());
        payPeriodRepository.save(ewaRequest.getPayPeriod());

        return new EwaResponseDto(ewaRequestId, ewaRequest.getRequestedAmount(), ewaRequest.getStatus());
    }
}