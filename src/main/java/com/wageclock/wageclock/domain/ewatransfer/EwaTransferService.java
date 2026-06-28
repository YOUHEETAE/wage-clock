package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
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
        String messageNo;
        try {
            messageNo = issueTransferMessageNo(ewaTransferId);
        }catch (Exception e){
            log.error("messageNo 발급/저장 실패 ewaTransferId={}", ewaTransferId, e);
            ewaTransferProcessor.failTransfer(ewaTransferId);
            return EwaRequest.EwaRequestStatus.FAILED;
        }
        try{
            WageTransferResult result = wageTransferPort.transfer(ewaTransfer.getWorker(), ewaTransfer.getAmount(), messageNo);
            return applyTransferStatus(result, ewaTransferId);
        }catch (Exception e){
            log.error("이체 처리 실패 EwaTransferId={}", ewaTransfer.getId(), e);
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
           applyTransferStatus(result, ewaTransferId);
       }catch (Exception e){
           log.error("이체 결과 조회 실패 EwaTransferId={}", ewaTransfer.getId(), e);
           ewaTransferProcessor.unknownTransfer(ewaTransferId);
       }
    }

    private String issueTransferMessageNo(Long ewaTransferId) {
        String messageNo = wageTransferPort.prepareTransfer(TransferType.EWA);
        ewaTransferProcessor.assignMessageNo(ewaTransferId ,messageNo);
        return messageNo;
    }

    private EwaRequest.EwaRequestStatus applyTransferStatus(WageTransferResult result, Long ewaTransferId) {
        return switch (result.classify()) {
            case SUCCESS -> {
                ewaTransferProcessor.completeTransfer(ewaTransferId);
                yield EwaRequest.EwaRequestStatus.APPROVED;
            }
            case PENDING_INQUIRY -> {
                ewaTransferProcessor.markPendingInquiry(ewaTransferId);
                yield EwaRequest.EwaRequestStatus.PENDING;
            }
            case FAILURE -> {
                ewaTransferProcessor.failTransfer(ewaTransferId);
                yield EwaRequest.EwaRequestStatus.FAILED;
                //todo : 확정 실패시 알림 발송 필요
            }
            case UNKNOWN -> {
                ewaTransferProcessor.unknownTransfer(ewaTransferId);
                yield EwaRequest.EwaRequestStatus.UNKNOWN;
            }
        };
    }
}
