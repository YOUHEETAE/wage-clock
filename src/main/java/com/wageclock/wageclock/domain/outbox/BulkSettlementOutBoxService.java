package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import org.springframework.stereotype.Service;

@Service
public class BulkSettlementOutBoxService {

    private final VirtualAccountPort virtualAccountPort;
    private final BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;
    private final BulkSettlementOutBoxResultHandler bulkSettlementOutBoxResultHandler;

    public BulkSettlementOutBoxService(VirtualAccountPort virtualAccountPort,
                                       BulkSettlementOutBoxResultHandler bulkSettlementOutBoxResultHandler,
                                       BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository) {
        this.virtualAccountPort = virtualAccountPort;
        this.bulkSettlementOutBoxResultHandler = bulkSettlementOutBoxResultHandler;
        this.bulkSettlementOutBoxEventRepository = bulkSettlementOutBoxEventRepository;
    }

    public void processEvent(BulkSettlementOutBoxEvent event) {
        try {
            VirtualAccountResult account = virtualAccountPort.issueVirtualAccount(event.getPortOnePaymentId(),
                    event.getTotalAmount(), "BULK-" + event.getBulkSettlementId(), event.getEmployerName());
            bulkSettlementOutBoxResultHandler.saveSuccess(event, account);
        } catch(Exception e){
            event.incrementRetryCount();
            bulkSettlementOutBoxEventRepository.save(event);
        }
    }

}
