package com.wageclock.wageclock.domain.settlement;

import java.math.BigDecimal;

public record BulkSettlementItemContext(Long workerId, BigDecimal amount, Long itemId, String transferId, String pendingMessageNo) {
}
