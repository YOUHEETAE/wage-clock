package com.wageclock.wageclock.domain.settlement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class BulkSettlementScheduler {

    // 이체 중 상태로 방치됐다고 판단하는 기준.
    // 아이템 하나당 최대 30초이고 스레드풀이 10개이므로, 100명이면 최악 5분 정도다.
    // 아이템 수가 크게 늘면 이 값도 다시 따져야 한다.
    private static final long STALE_TRANSFER_MINUTES = 30;

    private final BulkSettlementRepository bulkSettlementRepository;
    private final BulkSettlementService bulkSettlementService;
    private final BulkSettlementProcessor bulkSettlementProcessor;

    public BulkSettlementScheduler(BulkSettlementRepository bulkSettlementRepository,
                                   BulkSettlementService bulkSettlementService,
                                   BulkSettlementProcessor bulkSettlementProcessor) {
        this.bulkSettlementRepository = bulkSettlementRepository;
        this.bulkSettlementService = bulkSettlementService;
        this.bulkSettlementProcessor = bulkSettlementProcessor;
    }

    @Scheduled(fixedDelay = 300000)
    public void retryMissedWebhook() {
        List<BulkSettlement> settlements = bulkSettlementRepository
                .findByStatus(BulkSettlement.BulkSettlementStatus.PROCESSING);
        for (BulkSettlement settlement : settlements) {
            try {
                // 웹훅과 동일한 재조회 경로를 탄다
                bulkSettlementService.syncPaymentStatus(settlement.getPortOnePaymentId());
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

    /**
     * 이체 중 상태로 방치된 정산을 회수한다.
     * <p>
     * TRANSFERRING은 선점된 상태라 다른 스케줄러가 훑지 않는다. 그래서 이체 도중 프로세스가
     * 죽으면 아무도 손대지 못한 채 남는다. 예외 처리로는 이 경우를 못 잡는다 —
     * 프로세스가 사라지면 catch도 finally도 실행되지 않기 때문이다.
     * <p>
     * 여기서는 상태만 되돌리고 재처리는 retryFailedTransfers가 이어받는다.
     */
    @Scheduled(fixedDelay = 300000)
    public void recoverStaleTransferring() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_TRANSFER_MINUTES);
        List<BulkSettlement> settlements = bulkSettlementRepository.findStaleTransferring(threshold);
        for (BulkSettlement settlement : settlements) {
            try {
                log.warn("이체 중 상태로 방치된 정산 회수 paymentId={} updatedAt={}",
                        settlement.getPortOnePaymentId(), settlement.getUpdatedAt());
                bulkSettlementProcessor.recoverStaleTransfer(settlement.getPortOnePaymentId());
            } catch (Exception e) {
                log.warn("Failed to recover stale settlement: {}", settlement.getPortOnePaymentId(), e);
            }
        }
    }
}