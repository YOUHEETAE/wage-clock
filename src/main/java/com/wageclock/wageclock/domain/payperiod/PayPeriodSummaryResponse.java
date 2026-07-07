package com.wageclock.wageclock.domain.payperiod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayPeriodSummaryResponse (Long employmentId, String workerName,
                                        LocalDate periodStart, BigDecimal totalEarnedAmount,
                                        BigDecimal totalEwaAmount, BigDecimal remainingEwaLimit){
}
