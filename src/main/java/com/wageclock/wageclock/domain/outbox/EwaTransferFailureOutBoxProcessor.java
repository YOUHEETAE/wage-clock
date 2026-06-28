package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferProcessor;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EwaTransferFailureOutBoxProcessor {

    private final EwaTransferProcessor ewaTransferProcessor;
    private final EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;

    public EwaTransferFailureOutBoxProcessor(EwaTransferProcessor ewaTransferProcessor,
                                             EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository) {
        this.ewaTransferProcessor = ewaTransferProcessor;
        this.ewaTransferFailureOutBoxRepository = ewaTransferFailureOutBoxRepository;
    }

    @Transactional
    public void handleRetryOrFail(EwaTransferFailureOutBoxEvent event, Long ewaTransferId){
        event.incrementRetryCount();
        if(event.getStatus() == EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.FAILED){
            ewaTransferProcessor.failRetry(ewaTransferId);
            //todo : 확정 실패시 알림 발송 필요
        }else{
            ewaTransferProcessor.unKnownRetry(ewaTransferId);
        }
        ewaTransferFailureOutBoxRepository.save(event);
    }

    @Transactional
    public void handlePrepareRetryOrFail(EwaTransferFailureOutBoxEvent event, Long ewaTransferId){
        event.incrementRetryCount();
        if(event.getStatus() == EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.FAILED){
            ewaTransferProcessor.failRetry(ewaTransferId);
            //todo : 확정 실패시 알림 발송 필요
        }
        ewaTransferFailureOutBoxRepository.save(event);
    }

    @Transactional
    public void applyResult(WageTransferResult result, EwaTransfer ewaTransfer, EwaTransferFailureOutBoxEvent event) {
        switch (result.classify()) {
            case SUCCESS -> {
                ewaTransferProcessor.completeRetry(ewaTransfer.getId());
                event.processed();
                ewaTransferFailureOutBoxRepository.save(event);
            }
            case PENDING_INQUIRY -> {
                ewaTransferProcessor.markPendingInquiry(ewaTransfer.getId());
                event.processed();
                ewaTransferFailureOutBoxRepository.save(event);
            }
            case FAILURE -> {
                ewaTransferProcessor.failRetry(ewaTransfer.getId());
                event.failed();
                ewaTransferFailureOutBoxRepository.save(event);
            }
            case UNKNOWN -> handleRetryOrFail(event, ewaTransfer.getId());
        }
    }
}
