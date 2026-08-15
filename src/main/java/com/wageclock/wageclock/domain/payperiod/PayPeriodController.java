package com.wageclock.wageclock.domain.payperiod;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pay-periods")
public class PayPeriodController {

    private final PayPeriodService payPeriodService;

    public PayPeriodController(PayPeriodService payPeriodService) {
        this.payPeriodService = payPeriodService;
    }

    @Operation(summary = "정산 기간 요약 조회 (진행 중 적립액/선지급액 실시간 반영)")
    @GetMapping("/{employmentId}/summary")
    public PayPeriodSummaryResponse getSummary(@PathVariable Long employmentId,
            @AuthenticationPrincipal Long workerId){
        return payPeriodService.getPayPeriodSummaryResponse(employmentId, workerId);
    }

    @Operation(summary = "사업장 전체 정산 요약 목록 조회 (고용주)")
    @GetMapping("/summaries")
    public List<PayPeriodSummaryResponse> getSummaries(@RequestParam Long workplaceId,
                                                       @AuthenticationPrincipal Long employerId) {
        return payPeriodService.getPayPeriodSummaries(workplaceId, employerId);
    }
}