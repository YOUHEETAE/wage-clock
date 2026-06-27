package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItem;
import com.wageclock.wageclock.domain.settlement.BulkSettlementProcessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InterBankFailureOutBoxProcessor {

    private final BulkSettlementProcessor bulkSettlementProcessor;
    private final InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;

    public InterBankFailureOutBoxProcessor(BulkSettlementProcessor bulkSettlementProcessor,
                                           InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository){

        this.bulkSettlementProcessor = bulkSettlementProcessor;
        this.interBankFailureOutBoxEventRepository = interBankFailureOutBoxEventRepository;
    }

    @Transactional
    public void handleRetryOrFail(InterBankFailureOutBoxEvent event, BulkSettlementItem bulkSettlementItem) {
        event.incrementRetryCount();
        if (event.getStatus() == InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED) {
            bulkSettlementProcessor.failItem(bulkSettlementItem.getId());
            //todo : 확정 실패시 알림 발송 필요
        } else {
            bulkSettlementProcessor.unknownItem(bulkSettlementItem.getId());
        }
        interBankFailureOutBoxEventRepository.save(event);
    }

    @Transactional
    public void handlePrepareRetryOrFail(InterBankFailureOutBoxEvent event, BulkSettlementItem bulkSettlementItem) {
        event.incrementRetryCount();
        if (event.getStatus() == InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED) {
            bulkSettlementProcessor.failItem(bulkSettlementItem.getId());
            //todo : 확정 실패시 알림 발송 필요
        }
        interBankFailureOutBoxEventRepository.save(event);
    }

    @Transactional
    public void applyResult(WageTransferResult result, InterBankFailureOutBoxEvent event,
                            BulkSettlementItem bulkSettlementItem) {
        switch (result.classify()){
            case SUCCESS -> {
                bulkSettlementProcessor.completeRetry(bulkSettlementItem.getId());
                event.processed();
                interBankFailureOutBoxEventRepository.save(event);
            }
            case PENDING_INQUIRY -> bulkSettlementProcessor.markPendingInquiry(bulkSettlementItem.getId());
            case FAILURE -> {
                bulkSettlementProcessor.failItem(bulkSettlementItem.getId());
                event.failed();
                interBankFailureOutBoxEventRepository.save(event);
            }
            case UNKNOWN -> handleRetryOrFail(event, bulkSettlementItem);
        }
    }
}
