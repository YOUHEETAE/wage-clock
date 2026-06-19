package com.wageclock.wageclock.domain.EwaTransfer;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.port.TransferType;
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

    public EwaRequest.EwaRequestStatus processTransfer(EwaRequest ewaRequest) {
        EwaTransfer ewaTransfer = ewaTransferProcessor.createEwaTransfer(ewaRequest);
        Long ewaTransferId = ewaTransfer.getId();
        try {
            String messageNo = wageTransferPort.prepareTransfer(TransferType.EWA);
            ewaTransferProcessor.assignMessageNo(ewaTransferId ,messageNo);
            WageTransferResult result = wageTransferPort.transfer(ewaTransfer.getWorker(), ewaTransfer.getAmount(), messageNo);
            if(result.transferId() != null){
                ewaTransferProcessor.completeTransfer(ewaTransferId);
                return EwaRequest.EwaRequestStatus.APPROVED;
            }else if(result.pendingMessageNo() != null){
                ewaTransferProcessor.markPendingInquiry(ewaTransferId);
                return EwaRequest.EwaRequestStatus.PENDING;
            }else if(result.failureReason() != null){
                ewaTransferProcessor.failTransfer(ewaTransferId);
                return EwaRequest.EwaRequestStatus.FAILED;
                //todo : 확정 실패시 알림 발송 필요
            }
            ewaTransferProcessor.unknownTransfer(ewaTransferId);
            return EwaRequest.EwaRequestStatus.UNKNOWN;
        }catch (Exception e){
            log.error("이체 결과 조회 실패 EwaTransferId={}", ewaTransfer.getId(), e);
            ewaTransferProcessor.unknownTransfer(ewaTransferId);
            return EwaRequest.EwaRequestStatus.UNKNOWN;
        }
    }

    public void receiveInterBankFailure(String transferId) {
        ewaTransferProcessor.receiveInterBankFailure(transferId);
    }

    public void inquiryTransfer(EwaTransfer ewaTransfer) {
        Long ewaTransferId = ewaTransfer.getId();
       try {
           WageTransferResult result = wageTransferPort.inquireTransfer(ewaTransfer.getMessageNo());
           if (result.transferId() != null) {
               ewaTransferProcessor.completeTransfer(ewaTransferId);
           } else if (result.pendingMessageNo() != null) {
               ewaTransferProcessor.markPendingInquiry(ewaTransferId);
           } else if (result.failureReason() != null) {
               ewaTransferProcessor.failTransfer(ewaTransferId);
               //todo : 확정 실패시 알림 발송 필요
           }
       }catch (Exception e){
           log.error("이체 결과 조회 실패 EwaTransferId={}", ewaTransfer.getId(), e);
           ewaTransferProcessor.unknownTransfer(ewaTransferId);
       }
    }
}
