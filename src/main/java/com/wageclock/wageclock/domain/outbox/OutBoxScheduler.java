package com.wageclock.wageclock.domain.outbox;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutBoxScheduler {

    private final EwaOutBoxEventRepository ewaOutBoxEventRepository;
    private final EwaOutBoxService ewaOutBoxService;

    public OutBoxScheduler(EwaOutBoxEventRepository ewaOutBoxEventRepository, EwaOutBoxService ewaOutBoxService) {
        this.ewaOutBoxEventRepository = ewaOutBoxEventRepository;
        this.ewaOutBoxService = ewaOutBoxService;
    }
    @Scheduled(fixedDelay = 30000)
    public void processEwaOutBoxEvent(){
        List<EwaOutBoxEvent> events = ewaOutBoxEventRepository
                .findByStatus(EwaOutBoxEvent.OutBoxStatus.PENDING);
        events.forEach(ewaOutBoxService::processEvent);
    }
}
