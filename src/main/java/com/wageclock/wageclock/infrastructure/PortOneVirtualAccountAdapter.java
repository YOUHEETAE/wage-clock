package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.domain.port.PaymentStatus;
import com.wageclock.wageclock.domain.port.VirtualAccountPaymentResult;
import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class PortOneVirtualAccountAdapter implements VirtualAccountPort {

    private final PortOneService portOneService;

    public PortOneVirtualAccountAdapter(PortOneService portOneService) {
        this.portOneService = portOneService;
    }
    public VirtualAccountResult issueVirtualAccount(String portOnePaymentId, BigDecimal totalAmount,
                                                    String orderName, String employerName) {
        portOneService.createVirtualAccount(portOnePaymentId, totalAmount.longValue(), orderName, employerName);
        PortOneVirtualAccountInfoResponse info = portOneService.getVirtualAccountInfo(portOnePaymentId);
        return new VirtualAccountResult(info.method().bank(), info.method().accountNumber(), info.method().expiredAt());
    }
    public VirtualAccountPaymentResult getPaymentResult(String portOnePaymentId) {
        PortOneVirtualAccountInfoResponse info = portOneService.getVirtualAccountInfo(portOnePaymentId);
        Long paid = info.amount() != null ? info.amount().paid() : null;
        return new VirtualAccountPaymentResult(toPaymentStatus(info.status()),
                paid != null ? BigDecimal.valueOf(paid) : null);
    }

    /**
     * PortOne V2 PaymentStatus를 도메인 어휘로 번역한다.
     * 모르는 값은 PENDING으로 둔다 — 정산도 취소도 하지 않는 쪽이 안전하다.
     */
    private PaymentStatus toPaymentStatus(String portOneStatus) {
        return switch (portOneStatus) {
            case "PAID" -> PaymentStatus.PAID;
            case "FAILED", "CANCELLED", "PARTIAL_CANCELLED" -> PaymentStatus.FAILED;
            case "READY", "PENDING", "PAY_PENDING", "VIRTUAL_ACCOUNT_ISSUED" -> PaymentStatus.PENDING;
            default -> {
                log.warn("알 수 없는 PortOne 결제 상태: {}", portOneStatus);
                yield PaymentStatus.PENDING;
            }
        };
    }
}
