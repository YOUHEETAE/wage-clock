package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.port.TransferAccount;

import java.math.BigDecimal;

/**
 * 타행이체불능 통지 재처리에 필요한 값. 재이체와 결과 조회 중 어느 쪽으로 갈지 상태로 갈리므로
 * 두 경로가 쓰는 값을 함께 담는다.
 */
public record BulkSettlementItemRetryContext(Long itemId, BulkSettlementItem.BulkSettlementItemStatus status,
                                             BigDecimal amount, String messageNo, TransferAccount transferAccount) {

    public boolean needsInquiry() {
        return status == BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY
                || status == BulkSettlementItem.BulkSettlementItemStatus.UNKNOWN;
    }
}
