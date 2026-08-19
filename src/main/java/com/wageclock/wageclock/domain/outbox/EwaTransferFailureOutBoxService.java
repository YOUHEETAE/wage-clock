package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransferProcessor;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRetryContext;
import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EwaTransferFailureOutBoxService {

    private final WageTransferPort wageTransferPort;
    private final EwaTransferProcessor ewaTransferProcessor;
    private final EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    private final EwaTransferFailureOutBoxProcessor ewaTransferFailureOutBoxProcessor;

    public EwaTransferFailureOutBoxService(WageTransferPort wageTransferPort,
                                           EwaTransferProcessor ewaTransferProcessor,
                                           EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository,
                                           EwaTransferFailureOutBoxProcessor ewaTransferFailureOutBoxProcessor) {
        this.wageTransferPort = wageTransferPort;
        this.ewaTransferProcessor = ewaTransferProcessor;
        this.ewaTransferFailureOutBoxRepository = ewaTransferFailureOutBoxRepository;
        this.ewaTransferFailureOutBoxProcessor = ewaTransferFailureOutBoxProcessor;
    }

    public void processEvent(EwaTransferFailureOutBoxEvent event) {
        EwaTransferRetryContext context = ewaTransferProcessor.loadRetryContext(event.getEwaTransferId());
        if (context.isSettled()) {
            event.processed();
            ewaTransferFailureOutBoxRepository.save(event);
            return;
        }
        if (context.needsInquiry()) {
            inquireTransfer(context, event);
        } else {
            retryTransfer(context, event);
        }
    }

    private String issueMessageNo(Long ewaTransferId){
        String messageNo = wageTransferPort.prepareTransfer(TransferType.EWA);
        ewaTransferProcessor.assignMessageNo(ewaTransferId, messageNo);
        return messageNo;
    }
    private void retryTransfer(EwaTransferRetryContext context, EwaTransferFailureOutBoxEvent event){
        Long ewaTransferId = context.ewaTransferId();
        // 계좌가 없으면 전문번호를 발급하지 않고 재시도 카운트만 태운다.
        // 근로자가 그 사이 계좌를 등록하면 다음 재시도에서 성공하므로 즉시 확정 실패시키지 않는다.
        if (!context.transferAccount().isRegistered()) {
            log.error("계좌 정보 미등록 ewaTransferId={}", ewaTransferId);
            ewaTransferFailureOutBoxProcessor.handlePrepareRetryOrFail(event, ewaTransferId);
            return;
        }
        String messageNo;
        try {
            messageNo = issueMessageNo(ewaTransferId);
        }catch (Exception e){
            log.error("messageNo 발급/저장 실패 ewaTransferId={}", ewaTransferId, e);
            ewaTransferFailureOutBoxProcessor.handlePrepareRetryOrFail(event, ewaTransferId);
            return;
        }
        try {
            WageTransferResult result = wageTransferPort.transfer(context.transferAccount(),
                    context.amount(), messageNo);
            ewaTransferFailureOutBoxProcessor.applyResult(result, ewaTransferId, event);
        }
        catch (Exception e){
            log.error("이체 처리 실패 EwaTransferId={}", ewaTransferId, e);
            ewaTransferFailureOutBoxProcessor.handleRetryOrFail(event, ewaTransferId);
        }
    }
    private void inquireTransfer(EwaTransferRetryContext context, EwaTransferFailureOutBoxEvent event){
        Long ewaTransferId = context.ewaTransferId();
        try {
            WageTransferResult result = wageTransferPort.inquireTransfer(context.messageNo());
            ewaTransferFailureOutBoxProcessor.applyResult(result, ewaTransferId, event);
        }catch (Exception e){
            log.error("이체 결과 조회 실패 EwaTransferId={}", ewaTransferId, e);
            ewaTransferFailureOutBoxProcessor.handleRetryOrFail(event, ewaTransferId);
        }
    }
}
