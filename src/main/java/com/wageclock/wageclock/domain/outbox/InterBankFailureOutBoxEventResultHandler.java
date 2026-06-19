package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.settlement.BulkSettlementItem;
import com.wageclock.wageclock.domain.settlement.BulkSettlementProcessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InterBankFailureOutBoxEventResultHandler {

    private final InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    private final BulkSettlementProcessor bulkSettlementProcessor;

    public InterBankFailureOutBoxEventResultHandler(InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository,
                                                    BulkSettlementProcessor bulkSettlementProcessor) {
        this.interBankFailureOutBoxEventRepository = interBankFailureOutBoxEventRepository;
        this.bulkSettlementProcessor = bulkSettlementProcessor;
    }
    @Transactional
    public void saveSuccess(BulkSettlementItem failedItem, InterBankFailureOutBoxEvent event){
        Long itemId = failedItem.getId();
        bulkSettlementProcessor.completeRetry(itemId);
        event.processed();
        interBankFailureOutBoxEventRepository.save(event);
    }
}
