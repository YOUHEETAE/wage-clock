package com.wageclock.wageclock.domain.statement;

import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StatementService {

    private final PayPeriodRepository payPeriodRepository;
    private final StatementRepository statementRepository;

    public StatementService(PayPeriodRepository payPeriodRepository, StatementRepository statementRepository) {
        this.payPeriodRepository = payPeriodRepository;
        this.statementRepository = statementRepository;
    }

    @Transactional(readOnly = true)
    public PayPeriodStatementResponse getPayPeriodStatement(Long payPeriodId, Long employerId) {
        validateEmployerAccess(payPeriodId, employerId);
        return statementRepository.getPayPeriodStatement(payPeriodId);
    }
    @Transactional(readOnly = true)
    public List<WorkSessionStatementResponse> getWorkSessionStatement(Long payPeriodId, Long employerId) {
        validateEmployerAccess(payPeriodId, employerId);
        return statementRepository.getWorkSessionStatement(payPeriodId);
    }
    @Transactional(readOnly = true)
    public List<EwaRequestStatementResponse> getEwaRequestStatement(Long payPeriodId, Long employerId) {
        validateEmployerAccess(payPeriodId, employerId);
        return statementRepository.getEwaRequestStatement(payPeriodId);
    }

    private void validateEmployerAccess(Long payPeriodId, Long employerId) {
        PayPeriod payPeriod = payPeriodRepository.findById(payPeriodId)
                .orElseThrow(() -> new NotFoundException("Pay Period Not Found"));
        if (!payPeriod.getEmployerId().equals(employerId)) {
            throw new UnauthorizedException("Unauthorized Access");
        }
    }

}
