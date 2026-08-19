package com.wageclock.wageclock.domain.ewatransfer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class EwaTransferScheduler {

    private final EwaTransferProcessor ewaTransferProcessor;
    private final EwaTransferService ewaTransferService;

    public EwaTransferScheduler(EwaTransferProcessor ewaTransferProcessor, EwaTransferService ewaTransferService) {
        this.ewaTransferProcessor = ewaTransferProcessor;
        this.ewaTransferService = ewaTransferService;
    }

    @Scheduled(fixedDelay = 300000)
    public void retryPendingInquiryTransfer(){
        List<EwaTransferInquiryContext> contexts = ewaTransferProcessor.loadInquiryContexts();
        for(EwaTransferInquiryContext context : contexts){
            try {
                ewaTransferService.inquiryTransfer(context);
            }catch (Exception e){
                log.warn("Failed to retry ewaTransfer: {}", context.ewaTransferId(), e);
            }
        }
    }
}
