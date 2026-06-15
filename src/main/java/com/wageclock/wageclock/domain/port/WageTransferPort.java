package com.wageclock.wageclock.domain.port;

import com.wageclock.wageclock.domain.worker.Worker;

import java.math.BigDecimal;

public interface WageTransferPort {
    WageTransferResult transfer(Worker worker, BigDecimal amount, String referenceId);
    WageTransferResult inquireTransfer(String pendingMessageNo);
}
