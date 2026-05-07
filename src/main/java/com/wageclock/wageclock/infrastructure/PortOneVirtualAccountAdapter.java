package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.domain.payment.VirtualAccountPort;
import com.wageclock.wageclock.domain.payment.VirtualAccountResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PortOneVirtualAccountAdapter implements VirtualAccountPort {

    private final PortOneService portOneService;

    public PortOneVirtualAccountAdapter(PortOneService portOneService) {
        this.portOneService = portOneService;
    }
    public VirtualAccountResult issueVirtualAccount(String portOnePaymentId, BigDecimal totalAmount,
                                                    Long ewaRequestId, String employerName) {
        portOneService.createVirtualAccount(portOnePaymentId, totalAmount.longValue(), ewaRequestId, employerName);
        PortOneVirtualAccountInfoResponse info = portOneService.getVirtualAccountInfo(portOnePaymentId);
        return new VirtualAccountResult(info.method().bank(), info.method().accountNumber(), info.method().expiredAt());
    }
}
