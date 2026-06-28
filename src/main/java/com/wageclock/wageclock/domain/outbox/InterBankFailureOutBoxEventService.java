package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.TransferType;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItem;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItemRepository;
import com.wageclock.wageclock.domain.settlement.BulkSettlementProcessor;
import com.wageclock.wageclock.global.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InterBankFailureOutBoxEventService {

    private final WageTransferPort wageTransferPort;
    private final BulkSettlementItemRepository bulkSettlementItemRepository;
    private final BulkSettlementProcessor bulkSettlementProcessor;
    private final InterBankFailureOutBoxProcessor interBankFailureOutBoxProcessor;

    public InterBankFailureOutBoxEventService(WageTransferPort wageTransferPort,
                                              BulkSettlementItemRepository bulkSettlementItemRepository,
                                              BulkSettlementProcessor bulkSettlementProcessor, InterBankFailureOutBoxProcessor interBankFailureOutBoxProcessor) {
        this.wageTransferPort = wageTransferPort;
        this.bulkSettlementItemRepository = bulkSettlementItemRepository;
        this.bulkSettlementProcessor = bulkSettlementProcessor;
        this.interBankFailureOutBoxProcessor = interBankFailureOutBoxProcessor;
    }

    public void processEvent(InterBankFailureOutBoxEvent event) {
        BulkSettlementItem bulkSettlementItem = bulkSettlementItemRepository.findByIdWithEmployment(event.getBulkSettlementItemId())
                .orElseThrow(() -> new NotFoundException("BulkSettlementItem Not Found"));

        BulkSettlementItem.BulkSettlementItemStatus status = bulkSettlementItem.getStatus();

        if (status == BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY
                || status == BulkSettlementItem.BulkSettlementItemStatus.UNKNOWN) {
            inquiryTransfer(event, bulkSettlementItem);
        } else {
            retryTransfer(event, bulkSettlementItem);
        }
    }

    private String issueMessageNo(BulkSettlementItem bulkSettlementItem) {
        String messageNo = wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT);
        bulkSettlementProcessor.assignMessageNo(bulkSettlementItem.getId(), messageNo);
        return messageNo;
    }
    private void retryTransfer(InterBankFailureOutBoxEvent event, BulkSettlementItem bulkSettlementItem) {
        String messageNo;
        try{
            messageNo = issueMessageNo(bulkSettlementItem);
        }catch (Exception e){
            log.error("messageNo 발급/저장 실패 bulkSettlementItemId={}", bulkSettlementItem.getId(), e);
            interBankFailureOutBoxProcessor.handlePrepareRetryOrFail(event, bulkSettlementItem);
            return;
        }
        try{
            WageTransferResult result = wageTransferPort.transfer(bulkSettlementItem.getWorker(),
                    bulkSettlementItem.getAmount(), messageNo);
            interBankFailureOutBoxProcessor.applyResult(result, event, bulkSettlementItem);
        }catch (Exception e){
            log.error("이체 처리 실패 bulkSettlementItemId={}", bulkSettlementItem.getId(), e);
            interBankFailureOutBoxProcessor.handleRetryOrFail(event, bulkSettlementItem);
        }
    }
    private void inquiryTransfer(InterBankFailureOutBoxEvent event, BulkSettlementItem bulkSettlementItem) {
        try {
            WageTransferResult result = wageTransferPort.inquireTransfer(bulkSettlementItem.getMessageNo());
            interBankFailureOutBoxProcessor.applyResult(result, event, bulkSettlementItem);
        }catch (Exception e){
            log.error("이체 결과 조회 실패 bulkSettlementItemId={}", bulkSettlementItem.getId(), e);
            interBankFailureOutBoxProcessor.handleRetryOrFail(event, bulkSettlementItem);
        }
    }

}
