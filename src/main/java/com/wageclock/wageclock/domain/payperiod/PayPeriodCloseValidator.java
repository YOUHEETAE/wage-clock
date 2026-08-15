package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestRepository;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRepository;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PayPeriodCloseValidator {

    private final WorkSessionRepository workSessionRepository;
    private final EwaRequestRepository ewaRequestRepository;
    private final EwaTransferRepository ewaTransferRepository;

    // 확정된 상태만 나열하고 나머지를 미확정으로 본다.
    // 새 상태가 추가돼도 기본값이 "막는다"가 되어야 안전하다 — 돈이 걸린 판단이라
    // 모르는 상태를 통과시키는 쪽이 위험하다.
    private static final List<EwaRequest.EwaRequestStatus> SETTLED_REQUEST =
            List.of(EwaRequest.EwaRequestStatus.FAILED,
                    EwaRequest.EwaRequestStatus.APPROVED,
                    EwaRequest.EwaRequestStatus.REJECTED);
    private static final List<EwaTransfer.EwaTransferStatus> SETTLED_TRANSFER =
            List.of(EwaTransfer.EwaTransferStatus.FAILED,
                    EwaTransfer.EwaTransferStatus.COMPLETED);

    public PayPeriodCloseValidator(WorkSessionRepository workSessionRepository, EwaRequestRepository ewaRequestRepository, EwaTransferRepository ewaTransferRepository) {
        this.workSessionRepository = workSessionRepository;
        this.ewaRequestRepository = ewaRequestRepository;
        this.ewaTransferRepository = ewaTransferRepository;
    }

    public void validate(PayPeriod payPeriod) {
        if(workSessionRepository.existsByEmploymentIdAndStatusNot(payPeriod.getEmploymentId(),
                WorkSession.WorkSessionStatus.COMPLETED)){
            throw new IllegalStateException("Working WorkSession exists");
        }
        if(ewaRequestRepository.existsByPayPeriodAndStatusNotIn(payPeriod, SETTLED_REQUEST)){
            throw new IllegalStateException("Not finalized EwaRequest exists");
        }
        if(ewaTransferRepository.existsByEwaRequest_PayPeriodAndStatusNotIn(payPeriod, SETTLED_TRANSFER)){
            throw new IllegalStateException("Not finalized EwaTransfer exists");
        }
    }
}
