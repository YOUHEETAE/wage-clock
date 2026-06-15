package com.wageclock.wageclock.domain.EwaTransfer;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class EwaTransferService {

    private final WageTransferPort wageTransferPort;
    private final EwaTransferProcessor ewaTransferProcessor;

    public EwaTransferService(WageTransferPort wageTransferPort, EwaTransferProcessor ewaTransferProcessor) {

        this.wageTransferPort = wageTransferPort;
        this.ewaTransferProcessor = ewaTransferProcessor;
    }

    public void processTransfer(EwaRequest ewaRequest) {
        EwaTransfer ewaTransfer = ewaTransferProcessor.createEwaTransfer(ewaRequest);
        Long ewaTransferId = ewaTransfer.getId();
        try {
            String referenceId = "EWA-" + ewaTransfer.getId();
            WageTransferResult result = wageTransferPort.transfer(ewaTransfer.getWorker(), ewaTransfer.getAmount(), referenceId);
            if(result.transferId() != null){
                ewaTransferProcessor.assignTransferId(result.transferId(), ewaTransferId);
            }else if(result.pendingMessageNo() != null){
                ewaTransferProcessor.markPendingInquiry(result.pendingMessageNo(), ewaTransferId);
            }else if(result.failureReason() != null){
                ewaTransferProcessor.markFailed(ewaTransferId);
                //todo : 확정 실패시 알림 발송 필요
            }
        }catch (Exception e){
            log.error("이체 결과 조회 실패 EwaTransferId={}", ewaTransfer.getId(), e);
            ewaTransferProcessor.markUnknown(ewaTransferId);
        }
    }

    public void receiveInterBankFailure(String transferId) {
        ewaTransferProcessor.receiveInterBankFailure(transferId);
    }

    public void inquiryTransfer(EwaTransfer ewaTransfer) {
        Long ewaTransferId = ewaTransfer.getId();
       try {
           WageTransferResult result = wageTransferPort.inquireTransfer(ewaTransfer.getPendingMessageNo());
           if (result.transferId() != null) {
               ewaTransferProcessor.assignTransferId(result.transferId(), ewaTransferId);
           } else if (result.pendingMessageNo() != null) {
               ewaTransferProcessor.markPendingInquiry(result.pendingMessageNo(), ewaTransferId);
           } else if (result.failureReason() != null) {
               ewaTransferProcessor.markFailed(ewaTransferId);
               //todo : 확정 실패시 알림 발송 필요
           }
       }catch (Exception e){
           log.error("이체 결과 조회 실패 EwaTransferId={}", ewaTransfer.getId(), e);
           ewaTransferProcessor.markUnknown(ewaTransferId);
       }
    }
}
