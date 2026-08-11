package com.wageclock.wageclock.domain.statement;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 실서비스가 아니므로 프론트에서는 명세서 화면을 구현하지 않는다.
// 백엔드는 "실제 서비스라면 이 정도까지 필요하다"를 보여주는 선에서 남겨둔다.
// 실 서비스라면 여기서 더 나아가야 할 것들:
//  - 원천징수/4대보험 공제 내역, 연장/야간 가산수당 등 법정 항목
//  - 명세서 PDF 출력, 발급 이력 보관 (근로기준법상 임금명세서 교부 의무)
//  - 정산 확정 시점 스냅샷 저장 (현재는 조회 시점에 매번 계산)
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
    @Operation(summary = "근무 세션 명세 조회 (출퇴근/시급/적립액)")
    @GetMapping("/{payPeriodId}/work-sessions")
    public List<WorkSessionStatementResponse> getWorkSessionStatement(@PathVariable Long payPeriodId,
                                                                       @AuthenticationPrincipal Long employerId) {
        return statementService.getWorkSessionStatements(payPeriodId, employerId);
    }
    // 현재는 상태값만 내려준다. 실서비스라면 근로자가 왜 거절/실패했는지 알아야 하므로
    // 사유를 함께 보여줘야 한다. 붙인다면 손댈 곳:
    //  - 거절: EwaRequestProcessor.validateAndRejectEwa()가 사유를 받지 않음
    //  - 실패: 사유가 WageTransferResult.failureReason에 담겨 오지만
    //    EwaTransferFailureOutBoxProcessor가 classify() 결과만 보고 failRetry(id)를 호출해 유실됨
    @Operation(summary = "선지급 명세 조회")
    @GetMapping("/{payPeriodId}/ewa-requests")
    public List<EwaRequestStatementResponse> getEwaRequestStatement(@PathVariable Long payPeriodId,
                                                                     @AuthenticationPrincipal Long employerId) {
        return statementService.getEwaRequestStatements(payPeriodId, employerId);
    }
}
