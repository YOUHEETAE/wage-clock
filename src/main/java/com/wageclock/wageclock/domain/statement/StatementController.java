package com.wageclock.wageclock.domain.statement;

import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "정산 명세서 조회 (기간별 실지급 월급 산출 근거)")
    @GetMapping("/{payPeriodId}/pay-period")
    public PayPeriodStatementResponse getPayPeriodStatement(@PathVariable Long payPeriodId,
                                                             @AuthenticationPrincipal Long employerId) {
        return statementService.getPayPeriodStatement(payPeriodId, employerId);
    }
    @Operation(summary = "근무 세션 명세 조회 (출퇴근/일시정지 이력)")
    @GetMapping("/{payPeriodId}/work-sessions")
    public List<WorkSessionStatementResponse> getWorkSessionStatement(@PathVariable Long payPeriodId,
                                                                       @AuthenticationPrincipal Long employerId) {
        return statementService.getWorkSessionStatements(payPeriodId, employerId);
    }
    @Operation(summary = "선지급 명세 조회 (거절/실패 사유 포함)")
    @GetMapping("/{payPeriodId}/ewa-requests")
    public List<EwaRequestStatementResponse> getEwaRequestStatement(@PathVariable Long payPeriodId,
                                                                     @AuthenticationPrincipal Long employerId) {
        return statementService.getEwaRequestStatements(payPeriodId, employerId);
    }
}
