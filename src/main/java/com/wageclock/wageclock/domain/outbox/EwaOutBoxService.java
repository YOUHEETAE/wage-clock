package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import org.springframework.stereotype.Service;

@Service
public class EwaOutBoxService {

    private final VirtualAccountPort virtualAccountPort;
    private final EwaOutBoxResultHandler ewaOutBoxResultHandler;
    private final EwaOutBoxEventRepository ewaOutBoxEventRepository;

    public EwaOutBoxService(VirtualAccountPort virtualAccountPort, EwaOutBoxResultHandler ewaOutBoxResultHandler,
                            EwaOutBoxEventRepository ewaOutBoxEventRepository) {
        this.virtualAccountPort = virtualAccountPort;
        this.ewaOutBoxResultHandler = ewaOutBoxResultHandler;
        this.ewaOutBoxEventRepository = ewaOutBoxEventRepository;
    }

    public void processEvent(EwaOutBoxEvent event) {
        try {
            VirtualAccountResult account = virtualAccountPort.issueVirtualAccount(event.getPortOnePaymentId(), event.getAmount(),
                    "EWA-" + event.getEwaRequestId(), event.getEmployerName());
            ewaOutBoxResultHandler.saveSuccess(event, account);
        } catch (Exception e) {
            event.incrementRetryCount();
            ewaOutBoxEventRepository.save(event);
        }
    }
}
