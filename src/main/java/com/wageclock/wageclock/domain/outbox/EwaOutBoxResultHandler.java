package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.payment.Payment;
import com.wageclock.wageclock.domain.payment.PaymentRepository;
import com.wageclock.wageclock.domain.payment.VirtualAccountResult;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EwaOutBoxResultHandler {
    private final PaymentRepository paymentRepository;
    private final EwaOutBoxEventRepository ewaOutBoxEventRepository;

    public EwaOutBoxResultHandler(PaymentRepository paymentRepository, EwaOutBoxEventRepository ewaOutBoxEventRepository) {
        this.paymentRepository = paymentRepository;
        this.ewaOutBoxEventRepository = ewaOutBoxEventRepository;
    }

    @Transactional
    public void saveSuccess(EwaOutBoxEvent event, VirtualAccountResult account){
        Payment payment = paymentRepository.findByPortOnePaymentId(event.getPortOnePaymentId())
                .orElseThrow(()-> new NotFoundException("Payment not found"));
        payment.updateVirtualAccountInfo(account.bank(), account.accountNumber(), account.expiredAt());
        payment.processing();
        paymentRepository.save(payment);
        event.processed();
        ewaOutBoxEventRepository.save(event);
    }
}
