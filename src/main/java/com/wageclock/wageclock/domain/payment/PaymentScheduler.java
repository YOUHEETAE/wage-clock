package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.ewa.EwaRequestRepository;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentScheduler {

    private final PaymentRepository paymentRepository;
    private final EwaRequestRepository ewaRequestRepository;
    private final WorkSessionRepository workSessionRepository;

    public PaymentScheduler(PaymentRepository paymentRepository,
                            EwaRequestRepository ewaRequestRepository,
                            WorkSessionRepository workSessionRepository) {
        this.paymentRepository = paymentRepository;
        this.ewaRequestRepository = ewaRequestRepository;
        this.workSessionRepository = workSessionRepository;
    }
    @Scheduled(fixedDelay = 300000)
    public void retryPayment() {
        List<Payment> payment = paymentRepository.findByStatus(Payment.PaymentStatus.UNKNOWN);
        for (Payment pay : payment) {
            EwaRequest ewaRequest = pay.getEwaRequest();
            WorkSession workSession = pay.getWorkSession();
            pay.failed();
            paymentRepository.save(pay);
            ewaRequest.rejected();
            ewaRequestRepository.save(ewaRequest);
            workSession.subtractEwaAmount(pay.getAmount());
            workSessionRepository.save(workSession);
        }
    }

}
