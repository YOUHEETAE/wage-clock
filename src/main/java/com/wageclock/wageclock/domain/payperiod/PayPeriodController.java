package com.wageclock.wageclock.domain.payperiod;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pay-periods")
public class PayPeriodController {

    private final PayPeriodService payPeriodService;

    public PayPeriodController(PayPeriodService payPeriodService) {
        this.payPeriodService = payPeriodService;
    }

    @Operation(summary = "정산 마감 (PayPeriod 종료 + 실지급 월급 계산)")
    @PostMapping("/{employmentId}/close")
    public ClosePayPeriodResponse close(@PathVariable Long employmentId,
                                        @AuthenticationPrincipal Long employerId){
        return payPeriodService.closePayPeriod(employmentId, employerId);
    }
    @Operation(summary = "정산 기간 요약 조회 (진행 중 적립액/선지급액 실시간 반영)")
    @GetMapping("/{employmentId}/summary")
    public PayPeriodSummaryResponse getSummary(@PathVariable Long employmentId,
            @AuthenticationPrincipal Long callerId){
        return payPeriodService.getPayPeriodSummaryResponse(employmentId, callerId);
    }
}