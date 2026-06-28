package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferProcessor;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRepository;
import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.global.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EwaTransferFailureOutBoxService {

    private final EwaTransferRepository ewaTransferRepository;
    private final WageTransferPort wageTransferPort;
    private final EwaTransferProcessor ewaTransferProcessor;
    private final EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    private final EwaTransferFailureOutBoxProcessor ewaTransferFailureOutBoxProcessor;

    public EwaTransferFailureOutBoxService(EwaTransferRepository ewaTransferRepository,
                                           WageTransferPort wageTransferPort,
                                           EwaTransferProcessor ewaTransferProcessor,
                                           EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository,
                                           EwaTransferFailureOutBoxProcessor ewaTransferFailureOutBoxProcessor) {
        this.ewaTransferRepository = ewaTransferRepository;
        this.wageTransferPort = wageTransferPort;
        this.ewaTransferProcessor = ewaTransferProcessor;
        this.ewaTransferFailureOutBoxRepository = ewaTransferFailureOutBoxRepository;
        this.ewaTransferFailureOutBoxProcessor = ewaTransferFailureOutBoxProcessor;
    }

    public void processEvent(EwaTransferFailureOutBoxEvent event) {
        EwaTransfer ewaTransfer = ewaTransferRepository.findByIdWithWorker(event.getEwaTransferId())
                .orElseThrow(() -> new NotFoundException("EwaTransfer Not Found"));
        if (ewaTransfer.getStatus() == EwaTransfer.EwaTransferStatus.COMPLETED
                || ewaTransfer.getStatus() == EwaTransfer.EwaTransferStatus.FAILED) {
            event.processed();
            ewaTransferFailureOutBoxRepository.save(event);
            return;
        }
        if(ewaTransfer.getStatus() == EwaTransfer.EwaTransferStatus.PENDING_INQUIRY
                || ewaTransfer.getStatus() == EwaTransfer.EwaTransferStatus.UNKNOWN){
            inquireTransfer(ewaTransfer, event);
        }else{
            retryTransfer(ewaTransfer, event);
        }
    }

    private String issueMessageNo(Long ewaTransferId){
        String messageNo = wageTransferPort.prepareTransfer(TransferType.EWA);
        ewaTransferProcessor.assignMessageNo(ewaTransferId, messageNo);
        return messageNo;
    }
    private void retryTransfer(EwaTransfer ewaTransfer, EwaTransferFailureOutBoxEvent event){
        String messageNo;
        try {
            messageNo = issueMessageNo(ewaTransfer.getId());
        }catch (Exception e){
            log.error("messageNo 발급/저장 실패 ewaTransferId={}", ewaTransfer.getId(), e);
            ewaTransferFailureOutBoxProcessor.handlePrepareRetryOrFail(event, ewaTransfer.getId());
            return;
        }
        try {
            WageTransferResult result = wageTransferPort.transfer(ewaTransfer.getWorker(), ewaTransfer.getAmount(), messageNo);
            ewaTransferFailureOutBoxProcessor.applyResult(result, ewaTransfer, event);
        }
        catch (Exception e){
            log.error("이체 처리 실패 EwaTransferId={}", ewaTransfer.getId(), e);
            ewaTransferFailureOutBoxProcessor.handleRetryOrFail(event, ewaTransfer.getId());
        }
    }
    private void inquireTransfer(EwaTransfer ewaTransfer, EwaTransferFailureOutBoxEvent event){
        try {
            WageTransferResult result = wageTransferPort.inquireTransfer(ewaTransfer.getMessageNo());
            ewaTransferFailureOutBoxProcessor.applyResult(result, ewaTransfer, event);
        }catch (Exception e){
            log.error("이체 결과 조회 실패 EwaTransferId={}", ewaTransfer.getId(), e);
            ewaTransferFailureOutBoxProcessor.handleRetryOrFail(event, ewaTransfer.getId());
        }
    }
}
