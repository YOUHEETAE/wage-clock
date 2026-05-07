package com.wageclock.wageclock.domain.payment;

public record VirtualAccountResult(String bank, String accountNumber, String expiredAt) {
}
