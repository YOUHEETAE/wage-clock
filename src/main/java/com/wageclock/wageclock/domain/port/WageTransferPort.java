package com.wageclock.wageclock.domain.port;

import com.wageclock.wageclock.domain.worker.Worker;

import java.math.BigDecimal;

public interface WageTransferPort {
    String prepareTransfer(TransferType type);
    WageTransferResult transfer(Worker worker, BigDecimal amount, String messageNo);
    WageTransferResult inquireTransfer(String pendingMessageNo);
}
