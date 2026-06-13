package com.wageclock.wageclock.domain.port;

import com.wageclock.wageclock.domain.worker.Worker;

import java.math.BigDecimal;

public interface WageTransferPort {
    WageTransferResult transfer(Worker worker, BigDecimal amount, Long bulkSettlementItemId);
    WageTransferResult inquireTransfer(String pendingMessageNo, Long bulkSettlementItemId);
}
