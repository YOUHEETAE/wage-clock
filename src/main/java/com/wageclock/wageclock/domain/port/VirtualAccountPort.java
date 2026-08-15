package com.wageclock.wageclock.domain.port;



import java.math.BigDecimal;

public interface VirtualAccountPort {
    VirtualAccountResult issueVirtualAccount(String portOnePaymentId,
                                             BigDecimal totalAmount, String orderName, String employerName);
    VirtualAccountPaymentResult getPaymentResult(String portOnePaymentId);
}
