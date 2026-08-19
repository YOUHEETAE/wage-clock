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
    // 아웃박스를 종류별로 나눈 이유는 실패했을 때 되돌려야 할 도메인 상태가 다르기 때문이다.
    // 재시도 횟수 관리(MAX_RETRY 5회)는 셋 다 같지만,
    // 가상계좌 발급은 아직 돈이 움직이지 않아 이벤트만 재시도하면 되는 반면
    // 이체 재시도는 EwaTransfer·BulkSettlementItem 상태와 PayPeriod 한도까지 함께 확정해야 한다.
    //
    // fixedDelay는 이전 실행이 끝난 뒤부터 잰다. fixedRate로 두면 처리가 밀릴 때
    // 같은 이벤트를 다음 주기가 다시 집어 중복 처리될 수 있다.
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
