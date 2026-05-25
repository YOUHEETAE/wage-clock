package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.ewa.EwaSettlementService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentScheduler {

    private final PaymentRepository paymentRepository;
    private final VirtualAccountPort virtualAccountPort;
    private final EwaSettlementService ewaSettlementService;

    public PaymentScheduler(PaymentRepository paymentRepository,
                            VirtualAccountPort virtualAccountPort,
                            EwaSettlementService ewaSettlementService) {
        this.paymentRepository = paymentRepository;
        this.virtualAccountPort = virtualAccountPort;
        this.ewaSettlementService = ewaSettlementService;
    }
    @Scheduled(fixedDelay = 300000)
    public void retryPayment() {
        List<Payment> payment = paymentRepository.findByStatus(Payment.PaymentStatus.PROCESSING);
        for (Payment pay : payment) {
            String portOnePaymentId = pay.getPortOnePaymentId();
            String status = virtualAccountPort.getVirtualAccountStatus(portOnePaymentId);
            if(status.equals("PAID")) {
                ewaSettlementService.approveEwa(portOnePaymentId);
            }else if(status.equals("FAILED") || status.equals("CANCELLED")) {
                ewaSettlementService.failEwa(portOnePaymentId);
            }
        }
    }
}
