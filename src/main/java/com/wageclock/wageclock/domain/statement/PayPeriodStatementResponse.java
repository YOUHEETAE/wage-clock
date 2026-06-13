package com.wageclock.wageclock.domain.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayPeriodStatementResponse(Long payPeriodId, LocalDate periodStart,
                                         LocalDate periodEnd,
                                         BigDecimal totalEarnedAmount,
                                         BigDecimal totalEwaAmount,
                                         BigDecimal actualPayAmount,
                                         Long workerId,
                                         String workerName
){
}
