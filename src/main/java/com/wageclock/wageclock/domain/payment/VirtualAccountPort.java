package com.wageclock.wageclock.domain.payment;


import java.math.BigDecimal;

public interface VirtualAccountPort {
    VirtualAccountResult issueVirtualAccount(String portOnePaymentId,
                                             BigDecimal totalAmount, Long ewaRequestId, String employerName);
}
