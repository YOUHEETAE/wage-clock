package com.wageclock.wageclock.domain.port;

public record VirtualAccountResult(String bank, String accountNumber, String expiredAt) {
}
