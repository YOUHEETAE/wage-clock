package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.settlement.BulkSettlementItem;
import com.wageclock.wageclock.domain.settlement.BulkSettlementItemRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InterBankFailureOutBoxEventResultHandler {

    private final InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    private final BulkSettlementItemRepository bulkSettlementItemRepository;

    public InterBankFailureOutBoxEventResultHandler(InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository,
                                                    BulkSettlementItemRepository bulkSettlementItemRepository) {
        this.interBankFailureOutBoxEventRepository = interBankFailureOutBoxEventRepository;
        this.bulkSettlementItemRepository = bulkSettlementItemRepository;
    }
    @Transactional
    public void saveSuccess(String oldTransferId,String newTransferId, InterBankFailureOutBoxEvent event){
        BulkSettlementItem bulkSettlementItem = bulkSettlementItemRepository.findByTransferId(oldTransferId)
                .orElseThrow(() -> new NotFoundException("BulkSettlementItem Not Found"));
        bulkSettlementItem.assignTransferId(newTransferId);
        bulkSettlementItemRepository.save(bulkSettlementItem);
        event.processed();
        interBankFailureOutBoxEventRepository.save(event);
    }
}
