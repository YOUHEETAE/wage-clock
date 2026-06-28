package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlement;
import com.wageclock.wageclock.domain.settlement.BulkSettlementRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BulkSettlementOutBoxResultHandler {

    private final BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;
    private final BulkSettlementRepository bulkSettlementRepository;

    public BulkSettlementOutBoxResultHandler(BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository,
                                             BulkSettlementRepository bulkSettlementRepository) {
        this.bulkSettlementOutBoxEventRepository = bulkSettlementOutBoxEventRepository;
        this.bulkSettlementRepository = bulkSettlementRepository;
    }
    @Transactional
    public void saveSuccess(BulkSettlementOutBoxEvent event, VirtualAccountResult account) {
        String portOnePaymentId = event.getPortOnePaymentId();
        BulkSettlement bulkSettlement = bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException(portOnePaymentId + " not found"));
        bulkSettlement.updateAccountInfo(account.bank(), account.accountNumber(), account.expiredAt());
        bulkSettlement.processing();
        event.processed();
        bulkSettlementOutBoxEventRepository.save(event);
    }
}
