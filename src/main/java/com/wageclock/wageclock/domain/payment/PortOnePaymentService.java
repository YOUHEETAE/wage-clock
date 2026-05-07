package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PortOnePaymentService implements PaymentService{

    private final PaymentRepository paymentRepository;
    private final VirtualAccountPort  virtualAccountPort;

    public PortOnePaymentService(PaymentRepository paymentRepository, VirtualAccountPort virtualAccountPort) {
        this.paymentRepository = paymentRepository;
        this.virtualAccountPort = virtualAccountPort;
    }

    @Transactional
    public  Payment processPayment(EwaRequest ewaRequest) {
        String portOnePaymentId = UUID.randomUUID().toString();
        Payment payment = Payment.builder()
                .portOnePaymentId(portOnePaymentId)
                .employer(ewaRequest.getEmployer())
                .ewaRequest(ewaRequest)
                .amount(ewaRequest.getRequestedAmount())
                .build();
        paymentRepository.save(payment);
        return payment;
    }

    public VirtualAccountResult getAccount(String portOnePaymentId, BigDecimal totalAmount,
                                                        Long ewaRequestId, String employerName) {
        return virtualAccountPort
                .issueVirtualAccount(portOnePaymentId, totalAmount, ewaRequestId, employerName);
    }

    @Transactional
    public void updatePayment(Payment payment, VirtualAccountResult account) {
        payment.updateVirtualAccountInfo(account.bank(), account.accountNumber(), account.expiredAt());
        payment.processing();
        paymentRepository.save(payment);
    }
}
