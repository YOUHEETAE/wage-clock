package com.wageclock.wageclock.domain.port;

public record WageTransferResult(String transferId, String pendingMessageNo, String referenceId, String failureReason) {}