package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.port.TransferAccount;

import java.math.BigDecimal;

public record BulkSettlementItemContext(Long workerId, BigDecimal amount, TransferAccount transferAccount, Long itemId, String messageNo) {

    /**
     * 조회(PENDING_INQUIRY·UNKNOWN) 경로는 전문번호만 쓰므로 계좌를 채우지 않는다.
     * 그래서 이 컨텍스트의 계좌는 null일 수 있고, 이체 전에는 반드시 이 메서드로 확인한다.
     */
    public boolean hasRegisteredAccount() {
        return transferAccount != null && transferAccount.isRegistered();
    }
}
