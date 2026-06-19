package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.EwaTransfer.EwaTransfer;
import com.wageclock.wageclock.domain.EwaTransfer.EwaTransferProcessor;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import org.springframework.stereotype.Component;

@Component
public class EwaTransferFailureOutBoxResultHandler {

    private final EwaTransferProcessor ewaTransferProcessor;
    private final EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;

    public EwaTransferFailureOutBoxResultHandler(EwaTransferProcessor ewaTransferProcessor, EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository) {

        this.ewaTransferProcessor = ewaTransferProcessor;
        this.ewaTransferFailureOutBoxRepository = ewaTransferFailureOutBoxRepository;
    }

    public void saveSuccess(EwaTransfer ewaTransfer, EwaTransferFailureOutBoxEvent event) {
        Long ewaTransferId = ewaTransfer.getId();
        ewaTransferProcessor.completeRetry(ewaTransferId);
        event.processed();
        ewaTransferFailureOutBoxRepository.save(event);
    }
}
