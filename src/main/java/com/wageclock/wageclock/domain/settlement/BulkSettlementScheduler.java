package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class BulkSettlementScheduler {

    private final BulkSettlementRepository bulkSettlementRepository;
    private final BulkSettlementService bulkSettlementService;
    private final VirtualAccountPort virtualAccountPort;

    public BulkSettlementScheduler(BulkSettlementRepository bulkSettlementRepository,
                                   BulkSettlementService bulkSettlementService,
                                   VirtualAccountPort virtualAccountPort) {
        this.bulkSettlementRepository = bulkSettlementRepository;
        this.bulkSettlementService = bulkSettlementService;
        this.virtualAccountPort = virtualAccountPort;
    }

    @Scheduled(fixedDelay = 300000)
    public void retryMissedWebhook() {
        List<BulkSettlement> settlements = bulkSettlementRepository
                .findByStatus(BulkSettlement.BulkSettlementStatus.PROCESSING);
        for (BulkSettlement settlement : settlements) {
            try {
                String status = virtualAccountPort.getVirtualAccountStatus(settlement.getPortOnePaymentId());
                if ("PAID".equals(status)) {
                    bulkSettlementService.initiateBulkSettlement(settlement.getPortOnePaymentId());
                } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                    bulkSettlementService.failedPayment(settlement.getPortOnePaymentId());
                }
            } catch (Exception e) {
                log.warn("Failed to check bulk settlement status: {}", settlement.getPortOnePaymentId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void retryFailedTransfers() {
        List<BulkSettlement> settlements = bulkSettlementRepository
                .findByStatus(BulkSettlement.BulkSettlementStatus.TRANSFER_FAILED);
        for (BulkSettlement settlement : settlements) {
            try {
                bulkSettlementService.retrySettlement(settlement.getPortOnePaymentId());
            } catch (Exception e) {
                log.warn("Failed to retry settlement: {}", settlement.getPortOnePaymentId(), e);
            }
        }
    }
}