package com.wageclock.wageclock.domain.payment;


import com.wageclock.wageclock.infrastructure.PortOneVirtualAccountInfoResponse;

import java.math.BigDecimal;

public interface VirtualAccountPort {
    VirtualAccountResult issueVirtualAccount(String portOnePaymentId,
                                             BigDecimal totalAmount, Long ewaRequestId, String employerName);
    String getVirtualAccountStatus(String portOnePaymentId);
}
