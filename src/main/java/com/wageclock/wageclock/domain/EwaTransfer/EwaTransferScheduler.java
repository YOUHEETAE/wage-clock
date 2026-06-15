package com.wageclock.wageclock.domain.EwaTransfer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class EwaTransferScheduler {

    private final EwaTransferRepository ewaTransferRepository;
    private final EwaTransferService ewaTransferService;

    public EwaTransferScheduler(EwaTransferRepository ewaTransferRepository, EwaTransferService ewaTransferService) {
        this.ewaTransferRepository = ewaTransferRepository;
        this.ewaTransferService = ewaTransferService;
    }

    @Scheduled(fixedDelay = 300000)
    public void retryPendingInquiryTransfer(){
        List<EwaTransfer> ewaTransfers = ewaTransferRepository
                .findByStatusIn(List.of(EwaTransfer.EwaTransferStatus.PENDING_INQUIRY, EwaTransfer.EwaTransferStatus.UNKNOWN));
        for(EwaTransfer ewaTransfer : ewaTransfers){
            try {
                ewaTransferService.inquiryTransfer(ewaTransfer);
            }catch (Exception e){
                log.warn("Failed to retry ewaTransfer: {}", ewaTransfer.getId(), e);
            }
        }
    }
}
