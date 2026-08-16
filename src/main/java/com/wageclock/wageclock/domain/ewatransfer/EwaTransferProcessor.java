package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
public class EwaTransferProcessor {

    private final EwaTransferRepository ewaTransferRepository;
    private final EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    private final PayPeriodRepository payPeriodRepository;

    public EwaTransferProcessor(EwaTransferRepository ewaTransferRepository, EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository, PayPeriodRepository payPeriodRepository){

        this.ewaTransferRepository = ewaTransferRepository;
        this.ewaTransferFailureOutBoxRepository = ewaTransferFailureOutBoxRepository;
        this.payPeriodRepository = payPeriodRepository;
    }

    @Transactional
    public EwaTransfer createEwaTransfer(EwaRequest ewaRequest){
        EwaTransfer ewaTransfer = EwaTransfer.builder()
                .ewaRequest(ewaRequest)
                .amount(ewaRequest.getRequestedAmount())
                .build();
        ewaTransferRepository.save(ewaTransfer);
        return ewaTransfer;
    }

    @Transactional
    public void assignMessageNo(Long ewaTransferId ,String messageNo){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer not found"));
        ewaTransfer.assignMessageNo(messageNo);
    }

    @Transactional
    public void completeTransfer(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.completed();
        ewaTransfer.getEwaRequest().approved();
    }

    @Transactional
    public void markPendingInquiry(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.markPendingInquiry();
    }

    @Transactional
    public void failTransfer(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.failed();
        ewaTransfer.getEwaRequest().failed();
        PayPeriod payPeriod = payPeriodRepository.findByIdWithLock(ewaTransfer.getEwaRequest().getPayPeriodId())
                .orElseThrow(() -> new NotFoundException("PayPeriod not found"));
        // 요청 시점에 잡아둔 한도를 되돌린다 (이체가 나가지 않았음이 확정됨)
        payPeriod.subtractEwaAmount(ewaTransfer.getAmount());
    }

    @Transactional
    public void unknownTransfer(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.unknown();
        ewaTransfer.getEwaRequest().unknown();
    }
    @Transactional
    public void receiveInterBankFailure(String transferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findByMessageNo(transferId)
                .orElseThrow(() -> new NotFoundException("ewaTransfer not found"));
        // RETRYING은 아직 미확정이므로 한도를 되돌리지 않는다.
        // 여기서 되돌리면 재시도 성공 시 다시 더해야 하고, 그러면 최초 성공 경로와
        // 재시도 성공 경로가 completeTransfer를 공유하면서 한쪽이 반드시 틀어진다.
        ewaTransfer.retrying();
        EwaTransferFailureOutBoxEvent event = EwaTransferFailureOutBoxEvent.builder()
                .ewaTransferId(ewaTransfer.getId())
                .messageNo(transferId)
                .amount(ewaTransfer.getAmount()).build();
        ewaTransferFailureOutBoxRepository.save(event);
    }

    @Transactional
    public void completeRetry(Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.completed();
        managed.getEwaRequest().approved();
    }

    @Transactional
    public void failRetry(Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.failed();
        managed.getEwaRequest().failed();
        PayPeriod payPeriod = payPeriodRepository.findByIdWithLock(managed.getEwaRequest().getPayPeriodId())
                .orElseThrow(() -> new NotFoundException("PayPeriod not found"));
        // 요청 시점에 잡아둔 한도를 되돌린다.
        // 아웃박스가 FAILED 상태를 조기 종료 처리하므로 중복 차감되지 않는다.
        payPeriod.subtractEwaAmount(managed.getAmount());
    }

    @Transactional
    public void unKnownRetry(Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.unknown();
        managed.getEwaRequest().unknown();
    }
}
