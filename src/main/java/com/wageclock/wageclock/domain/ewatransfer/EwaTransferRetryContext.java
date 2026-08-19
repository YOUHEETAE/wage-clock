package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.port.TransferAccount;

import java.math.BigDecimal;

/**
 * 아웃박스 재처리에 필요한 값. 재이체와 결과 조회 중 어느 쪽으로 갈지 상태로 갈리므로
 * 두 경로가 쓰는 값을 함께 담는다.
 */
public record EwaTransferRetryContext(Long ewaTransferId, EwaTransfer.EwaTransferStatus status,
                                      BigDecimal amount, String messageNo, TransferAccount transferAccount) {

    public boolean isSettled() {
        return status == EwaTransfer.EwaTransferStatus.COMPLETED
                || status == EwaTransfer.EwaTransferStatus.FAILED;
    }

    public boolean needsInquiry() {
        return status == EwaTransfer.EwaTransferStatus.PENDING_INQUIRY
                || status == EwaTransfer.EwaTransferStatus.UNKNOWN;
    }
}
