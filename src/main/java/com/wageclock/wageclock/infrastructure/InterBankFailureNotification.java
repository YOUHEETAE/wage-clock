package com.wageclock.wageclock.infrastructure;

public record InterBankFailureNotification(String transferId, String referenceId) {}
