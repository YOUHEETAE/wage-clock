package com.wageclock.wageclock.domain.settlement;

import java.math.BigDecimal;

public record BulkSettlementResponse(
        Long bulkSettlementId,
        BigDecimal totalAmount,
        String bank,
        String accountNumber,
        String expiredAt
) {}

