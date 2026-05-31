package com.wageclock.wageclock.domain.payperiod;

import java.math.BigDecimal;

public record PayPeriodSummaryResponse (BigDecimal totalEarnedAmount, BigDecimal totalEwaAmount,
                                        BigDecimal remainingEwaLimit){
}
