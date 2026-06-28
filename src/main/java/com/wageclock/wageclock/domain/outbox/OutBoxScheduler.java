package com.wageclock.wageclock.domain.outbox;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutBoxScheduler {

    private final BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;
    private final BulkSettlementOutBoxService bulkSettlementOutBoxService;
    private final InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    private final InterBankFailureOutBoxEventService interBankFailureOutBoxEventService;
    private final EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    private final EwaTransferFailureOutBoxService ewaTransferFailureOutBoxService;

    public OutBoxScheduler(
            BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository,
            BulkSettlementOutBoxService bulkSettlementOutBoxService,
            InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository,
            InterBankFailureOutBoxEventService interBankFailureOutBoxEventService,
            EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository,
            EwaTransferFailureOutBoxService ewaTransferFailureOutBoxService) {
        this.bulkSettlementOutBoxEventRepository = bulkSettlementOutBoxEventRepository;
        this.bulkSettlementOutBoxService = bulkSettlementOutBoxService;
        this.interBankFailureOutBoxEventRepository = interBankFailureOutBoxEventRepository;
        this.interBankFailureOutBoxEventService = interBankFailureOutBoxEventService;
        this.ewaTransferFailureOutBoxRepository = ewaTransferFailureOutBoxRepository;
        this.ewaTransferFailureOutBoxService = ewaTransferFailureOutBoxService;
    }
    @Scheduled(fixedDelay = 30000)
    public void processBulkSettlementOutBoxEvent(){
        List<BulkSettlementOutBoxEvent> events = bulkSettlementOutBoxEventRepository
                .findByStatus(BulkSettlementOutBoxEvent.OutBoxStatus.PENDING);
        events.forEach(bulkSettlementOutBoxService::processEvent);
    }
    @Scheduled(fixedDelay = 30000)
    public void processInterBankFailureOutBoxEvent(){
        List<InterBankFailureOutBoxEvent> events = interBankFailureOutBoxEventRepository
                .findByStatus(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.PENDING);
        events.forEach(interBankFailureOutBoxEventService::processEvent);
    }
    @Scheduled(fixedDelay = 30000)
    public void processEwaTransferFailureOutBoxEvent(){
        List<EwaTransferFailureOutBoxEvent> events = ewaTransferFailureOutBoxRepository
                .findByStatus(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PENDING);
        events.forEach(ewaTransferFailureOutBoxService::processEvent);
    }
}
