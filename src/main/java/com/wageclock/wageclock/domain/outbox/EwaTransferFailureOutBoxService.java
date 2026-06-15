package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.EwaTransfer.EwaTransfer;
import com.wageclock.wageclock.domain.EwaTransfer.EwaTransferProcessor;
import com.wageclock.wageclock.domain.EwaTransfer.EwaTransferRepository;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.global.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EwaTransferFailureOutBoxService {

    private final EwaTransferRepository ewaTransferRepository;
    private final WageTransferPort wageTransferPort;
    private final EwaTransferProcessor ewaTransferProcessor;
    private final EwaTransferFailureOutBoxResultHandler ewaTransferFailureOutBoxResultHandler;
    private final EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;

    public EwaTransferFailureOutBoxService(EwaTransferRepository ewaTransferRepository,
                                           WageTransferPort wageTransferPort,
                                           EwaTransferProcessor ewaTransferProcessor,
                                           EwaTransferFailureOutBoxResultHandler ewaTransferFailureOutBoxResultHandler,
                                           EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository) {
        this.ewaTransferRepository = ewaTransferRepository;
        this.wageTransferPort = wageTransferPort;
        this.ewaTransferProcessor = ewaTransferProcessor;
        this.ewaTransferFailureOutBoxResultHandler = ewaTransferFailureOutBoxResultHandler;
        this.ewaTransferFailureOutBoxRepository = ewaTransferFailureOutBoxRepository;
    }

    public void processEvent(EwaTransferFailureOutBoxEvent event) {
        EwaTransfer failedTransfer = ewaTransferRepository.findByIdWithWorker(event.getEwaTransferId())
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        Long failedTransferId = failedTransfer.getId();
        Worker worker = failedTransfer.getWorker();
        try{
            WageTransferResult result = wageTransferPort.transfer(worker, failedTransfer.getAmount(),
                    "EWA-" + failedTransfer.getId());
            if(result.transferId() != null){
                ewaTransferFailureOutBoxResultHandler.saveSuccess(failedTransfer, result.transferId(), event);
            }else if(result.pendingMessageNo() != null){
                ewaTransferProcessor.markPendingInquiry(result.pendingMessageNo(), failedTransferId);
                event.processed();
                ewaTransferFailureOutBoxRepository.save(event);
            }else if(result.failureReason() != null){
                ewaTransferProcessor.markRetryFailed(failedTransferId);
            }
        }catch(Exception e){
            log.error("이체 결과 조회 실패 EwaTransferId={}", failedTransfer.getId(), e);
            event.incrementRetryCount();
            ewaTransferFailureOutBoxRepository.save(event);
            ewaTransferProcessor.markRetryUnknown(failedTransferId);
        }

    }
}
