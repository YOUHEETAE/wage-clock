package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferProcessor;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRepository;
import com.wageclock.wageclock.domain.port.TransferType;
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
        EwaTransfer ewaTransfer = ewaTransferRepository.findByIdWithWorker(event.getEwaTransferId())
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        Long transferId = ewaTransfer.getId();
        Worker worker = ewaTransfer.getWorker();
        if (ewaTransfer.getStatus() == EwaTransfer.EwaTransferStatus.COMPLETED
                || ewaTransfer.getStatus() == EwaTransfer.EwaTransferStatus.FAILED) {
            event.processed();
            ewaTransferFailureOutBoxRepository.save(event);
            return;
        }
        try{
            WageTransferResult result;
            if(ewaTransfer.getStatus() == EwaTransfer.EwaTransferStatus.PENDING_INQUIRY
                    || ewaTransfer.getStatus() == EwaTransfer.EwaTransferStatus.UNKNOWN){
                result = wageTransferPort.inquireTransfer(ewaTransfer.getMessageNo());
            }else{
                String messageNo = wageTransferPort.prepareTransfer(TransferType.EWA);
                ewaTransferProcessor.assignMessageNo(transferId, messageNo);
                result = wageTransferPort.transfer(worker, ewaTransfer.getAmount(), messageNo);
            }
            if(result.transferId() != null){
                ewaTransferFailureOutBoxResultHandler.saveSuccess(ewaTransfer, event);
            }else if(result.pendingMessageNo() != null){
                ewaTransferProcessor.markPendingInquiry(transferId);
                event.processed();
                ewaTransferFailureOutBoxRepository.save(event);
            }else if(result.failureReason() != null){
                ewaTransferProcessor.failRetry(transferId);
                event.failed();
                ewaTransferFailureOutBoxRepository.save(event);
            }else{
                event.incrementRetryCount();
                if(event.getStatus() == EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.FAILED){
                    ewaTransferProcessor.failRetry(transferId);
                    event.failed();
                    //todo : 확정 실패시 알림 발송 필요
                    ewaTransferFailureOutBoxRepository.save(event);
                }else{
                    ewaTransferProcessor.unKnownRetry(transferId);
                    ewaTransferFailureOutBoxRepository.save(event);
                }
            }
        }catch(Exception e){
            log.error("이체 결과 조회 실패 EwaTransferId={}", ewaTransfer.getId(), e);
            event.incrementRetryCount();
            if(event.getStatus() == EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.FAILED){
                ewaTransferProcessor.failRetry(transferId);
                event.failed();
                //todo : 확정 실패시 알림 발송 필요
                ewaTransferFailureOutBoxRepository.save(event);
            }else{
                ewaTransferProcessor.unKnownRetry(transferId);
                ewaTransferFailureOutBoxRepository.save(event);
            }
        }

    }
}
