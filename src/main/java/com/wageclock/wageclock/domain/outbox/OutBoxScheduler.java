package com.wageclock.wageclock.domain.outbox;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutBoxScheduler {

    private final EwaOutBoxEventRepository ewaOutBoxEventRepository;
    private final EwaOutBoxService ewaOutBoxService;
    private final BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;
    private final BulkSettlementOutBoxService bulkSettlementOutBoxService;
    private final InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    private final InterBankFailureOutBoxEventService interBankFailureOutBoxEventService;

    public OutBoxScheduler(EwaOutBoxEventRepository ewaOutBoxEventRepository,
                           EwaOutBoxService ewaOutBoxService,
                           BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository,
                           BulkSettlementOutBoxService bulkSettlementOutBoxService,
                           InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository,
                           InterBankFailureOutBoxEventService interBankFailureOutBoxEventService) {
        this.ewaOutBoxEventRepository = ewaOutBoxEventRepository;
        this.ewaOutBoxService = ewaOutBoxService;
        this.bulkSettlementOutBoxEventRepository = bulkSettlementOutBoxEventRepository;
        this.bulkSettlementOutBoxService = bulkSettlementOutBoxService;
        this.interBankFailureOutBoxEventRepository = interBankFailureOutBoxEventRepository;
        this.interBankFailureOutBoxEventService = interBankFailureOutBoxEventService;
    }
    @Scheduled(fixedDelay = 30000)
    public void processEwaOutBoxEvent(){
        List<EwaOutBoxEvent> events = ewaOutBoxEventRepository
                .findByStatus(EwaOutBoxEvent.OutBoxStatus.PENDING);
        events.forEach(ewaOutBoxService::processEvent);
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
}
