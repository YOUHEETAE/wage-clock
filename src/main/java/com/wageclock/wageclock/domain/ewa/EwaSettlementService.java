package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.payment.Payment;
import com.wageclock.wageclock.domain.payment.PaymentRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class EwaSettlementService {

    private final PaymentRepository paymentRepository;
    private final EwaRequestRepository ewaRequestRepository;
    private final EwaTransactionRepository ewaTransactionRepository;

    public EwaSettlementService(PaymentRepository paymentRepository, EwaRequestRepository ewaRequestRepository,
                                EwaTransactionRepository ewaTransactionRepository) {
        this.paymentRepository = paymentRepository;
        this.ewaRequestRepository = ewaRequestRepository;
        this.ewaTransactionRepository = ewaTransactionRepository;
    }

    @Transactional
    public void approveEwa(String portOnePaymentId){
        Payment payment = paymentRepository.findByPortOnePaymentId(portOnePaymentId)
                .orElseThrow(() -> new NotFoundException("invalid portOnePaymentId"));

        payment.completed();
        paymentRepository.save(payment);
        payment.getEwaRequest().approved();
        ewaRequestRepository.save(payment.getEwaRequest());
        ewaTransactionRepository.save(new EwaTransaction(payment.getEwaRequest(), payment.getAmount()));
    }
}
