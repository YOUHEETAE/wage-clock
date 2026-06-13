package com.wageclock.wageclock.domain.statement;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/statement")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping("/{payPeriodId}/payperiod")
    public PayPeriodStatementResponse getPayPeriodStatement(@PathVariable Long payPeriodId,
                                                             @AuthenticationPrincipal Long employerId) {
        return statementService.getPayPeriodStatement(payPeriodId, employerId);
    }
    @GetMapping("/{payPeriodId}/worksession")
    public List<WorkSessionStatementResponse> getWorkSessionStatement(@PathVariable Long payPeriodId,
                                                                       @AuthenticationPrincipal Long employerId) {
        return statementService.getWorkSessionStatement(payPeriodId, employerId);
    }
    @GetMapping("/{payPeriodId}/ewarequest")
    public List<EwaRequestStatementResponse> getEwaRequestStatement(@PathVariable Long payPeriodId,
                                                                     @AuthenticationPrincipal Long employerId) {
        return statementService.getEwaRequestStatement(payPeriodId, employerId);
    }
}
