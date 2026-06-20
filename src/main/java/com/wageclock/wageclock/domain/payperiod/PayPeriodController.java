package com.wageclock.wageclock.domain.payperiod;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pay-periods")
public class PayPeriodController {

    private final PayPeriodService payPeriodService;

    public PayPeriodController(PayPeriodService payPeriodService) {
        this.payPeriodService = payPeriodService;
    }

    @PostMapping("/{employmentId}/close")
    public ClosePayPeriodResponse close(@PathVariable Long employmentId,
                                        @AuthenticationPrincipal Long employerId){
        return payPeriodService.closePayPeriod(employmentId, employerId);
    }
    @GetMapping("/{employmentId}/summary")
    public PayPeriodSummaryResponse getSummary(@PathVariable Long employmentId,
            @AuthenticationPrincipal Long callerId){
        return payPeriodService.getPayPeriodSummaryResponse(employmentId, callerId);
    }
}