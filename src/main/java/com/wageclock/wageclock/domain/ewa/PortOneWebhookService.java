package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.payment.Payment;
import com.wageclock.wageclock.domain.payment.PaymentRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class PortOneWebhookService {

    private final PaymentRepository paymentRepository;
    private final EwaRequestRepository ewaRequestRepository;

    public PortOneWebhookService(PaymentRepository paymentRepository, EwaRequestRepository ewaRequestRepository) {
        this.paymentRepository = paymentRepository;
        this.ewaRequestRepository = ewaRequestRepository;
    }

    @Transactional
    public void approveEwa(String portOnePaymentId){
        Payment payment = paymentRepository.findByPortOnePaymentId(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException("invalid portOnePaymentId"));

        payment.completed();
        paymentRepository.save(payment);
        payment.getEwaRequest().approved();
        ewaRequestRepository.save(payment.getEwaRequest());
    }
}
