package com.wageclock.wageclock.domain.statement;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping("/{payPeriodId}/pay-period")
    public PayPeriodStatementResponse getPayPeriodStatement(@PathVariable Long payPeriodId,
                                                             @AuthenticationPrincipal Long employerId) {
        return statementService.getPayPeriodStatement(payPeriodId, employerId);
    }
    @GetMapping("/{payPeriodId}/work-sessions")
    public List<WorkSessionStatementResponse> getWorkSessionStatement(@PathVariable Long payPeriodId,
                                                                       @AuthenticationPrincipal Long employerId) {
        return statementService.getWorkSessionStatements(payPeriodId, employerId);
    }
    @GetMapping("/{payPeriodId}/ewa-requests")
    public List<EwaRequestStatementResponse> getEwaRequestStatement(@PathVariable Long payPeriodId,
                                                                     @AuthenticationPrincipal Long employerId) {
        return statementService.getEwaRequestStatements(payPeriodId, employerId);
    }
}
