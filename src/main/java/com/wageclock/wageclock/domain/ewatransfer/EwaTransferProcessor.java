package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
public class EwaTransferProcessor {

    private final EwaTransferRepository ewaTransferRepository;
    private final EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;

    public EwaTransferProcessor(EwaTransferRepository ewaTransferRepository, EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository){

        this.ewaTransferRepository = ewaTransferRepository;
        this.ewaTransferFailureOutBoxRepository = ewaTransferFailureOutBoxRepository;
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
        ewaTransfer.getEwaRequest().getPayPeriod().addEwaAmount(ewaTransfer.getAmount());
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
        ewaTransfer.retrying();
        ewaTransfer.getEwaRequest().refundEwa(ewaTransfer.getAmount());
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
        managed.getEwaRequest().getPayPeriod().addEwaAmount(managed.getAmount());
    }

    @Transactional
    public void failRetry(Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.failed();
    }

    @Transactional
    public void unKnownRetry(Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.unknown();
    }




}
