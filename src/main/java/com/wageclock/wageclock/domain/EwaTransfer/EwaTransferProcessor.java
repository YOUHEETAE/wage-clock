package com.wageclock.wageclock.domain.EwaTransfer;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
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
    public void assignTransferId(String transferId, Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.assignTransferId(transferId);
        ewaTransfer.getEwaRequest().approved();
        ewaTransfer.getEwaRequest().getPayPeriod().addEwaAmount(ewaTransfer.getAmount());
    }

    @Transactional
    public void markPendingInquiry(String pendingMessageNo, Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.markPendingInquiry(pendingMessageNo);
    }

    @Transactional
    public void markFailed(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.markFailed();
        ewaTransfer.getEwaRequest().failed();
    }
    @Transactional
    public void markUnknown(Long ewaTransferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        ewaTransfer.markUnknown();
        ewaTransfer.getEwaRequest().unknown();
    }
    @Transactional
    public void receiveInterBankFailure(String transferId){
        EwaTransfer ewaTransfer = ewaTransferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new NotFoundException("ewaTransfer not found"));
        ewaTransfer.markRetrying();
        ewaTransfer.getEwaRequest().refundEwa(ewaTransfer.getAmount());
        EwaTransferFailureOutBoxEvent event = EwaTransferFailureOutBoxEvent.builder()
                .ewaTransferId(ewaTransfer.getId())
                .transferId(transferId)
                .amount(ewaTransfer.getAmount()).build();
        ewaTransferFailureOutBoxRepository.save(event);
    }

    @Transactional
    public void completeRetry(String transferId, Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.assignTransferId(transferId);
        managed.getEwaRequest().getPayPeriod().addEwaAmount(managed.getAmount());
    }

    @Transactional
    public void markRetryFailed(Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.markFailed();
    }

    @Transactional
    public void markRetryUnknown(Long ewaTransferId){
        EwaTransfer managed = ewaTransferRepository.findById(ewaTransferId)
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        managed.markUnknown();
    }
}
