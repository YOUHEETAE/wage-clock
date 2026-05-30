package com.wageclock.wageclock.domain.payperiod;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payperiod")
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
}
