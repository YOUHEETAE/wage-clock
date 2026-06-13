package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.outbox.EwaOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.EwaOutBoxEventRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PortOnePaymentService implements PaymentService{

    private final PaymentRepository paymentRepository;
    private final VirtualAccountPort virtualAccountPort;
    private final EwaOutBoxEventRepository ewaOutBoxEventRepository;

    public PortOnePaymentService(PaymentRepository paymentRepository, VirtualAccountPort virtualAccountPort,
                                 EwaOutBoxEventRepository ewaOutBoxEventRepository) {
        this.paymentRepository = paymentRepository;
        this.virtualAccountPort = virtualAccountPort;
        this.ewaOutBoxEventRepository = ewaOutBoxEventRepository;
    }

    @Transactional
    public  Payment processPayment(EwaRequest ewaRequest) {
        String portOnePaymentId = "EWA-" + UUID.randomUUID();
        Payment payment = Payment.builder()
                .portOnePaymentId(portOnePaymentId)
                .employer(ewaRequest.getEmployer())
                .ewaRequest(ewaRequest)
                .amount(ewaRequest.getRequestedAmount())
                .build();
        paymentRepository.save(payment);
        EwaOutBoxEvent outBoxEvent = EwaOutBoxEvent.builder()
                .ewaRequestId(ewaRequest.getId())
                .portOnePaymentId(portOnePaymentId)
                .amount(payment.getAmount())
                .employerName(ewaRequest.getEmployerName())
                .build();
        ewaOutBoxEventRepository.save(outBoxEvent);
        return payment;
    }

    public VirtualAccountResult getAccount(String portOnePaymentId, BigDecimal totalAmount,
                                           String orderName, String employerName) {
        return virtualAccountPort
                .issueVirtualAccount(portOnePaymentId, totalAmount, orderName, employerName);
    }

    @Transactional
    public void updatePayment(Payment payment, VirtualAccountResult account) {
        payment.updateVirtualAccountInfo(account.bank(), account.accountNumber(), account.expiredAt());
        payment.processing();
        paymentRepository.save(payment);

        EwaOutBoxEvent event = ewaOutBoxEventRepository.findByPortOnePaymentId(payment.getPortOnePaymentId())
                .orElseThrow(() -> new NotFoundException("OutBoxEvent not found"));
        event.processed();
        ewaOutBoxEventRepository.save(event);
    }
}
