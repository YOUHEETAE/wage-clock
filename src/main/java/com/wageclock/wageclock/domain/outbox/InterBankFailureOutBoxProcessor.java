package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.WageTransferResult;
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
    public void handleRetryOrFail(InterBankFailureOutBoxEvent event, Long bulkSettlementItemId) {
        event.incrementRetryCount();
        if (event.getStatus() == InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED) {
            bulkSettlementProcessor.failItem(bulkSettlementItemId);
            //todo : 확정 실패시 알림 발송 필요
        } else {
            bulkSettlementProcessor.unknownItem(bulkSettlementItemId);
        }
        interBankFailureOutBoxEventRepository.save(event);
    }

    @Transactional
    public void handlePrepareRetryOrFail(InterBankFailureOutBoxEvent event, Long bulkSettlementItemId) {
        event.incrementRetryCount();
        if (event.getStatus() == InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.FAILED) {
            bulkSettlementProcessor.failItem(bulkSettlementItemId);
            //todo : 확정 실패시 알림 발송 필요
        }
        interBankFailureOutBoxEventRepository.save(event);
    }

    @Transactional
    public void applyResult(WageTransferResult result, InterBankFailureOutBoxEvent event, Long bulkSettlementItemId) {
        switch (result.classify()){
            case SUCCESS -> {
                bulkSettlementProcessor.completeRetry(bulkSettlementItemId);
                event.processed();
                interBankFailureOutBoxEventRepository.save(event);
            }
            case PENDING_INQUIRY -> bulkSettlementProcessor.markPendingInquiry(bulkSettlementItemId);
            case FAILURE -> {
                //todo : 확정 실패시 알림 발송 필요
                bulkSettlementProcessor.failItem(bulkSettlementItemId);
                event.failed();
                interBankFailureOutBoxEventRepository.save(event);
            }
            case UNKNOWN -> handleRetryOrFail(event, bulkSettlementItemId);
        }
    }
}
