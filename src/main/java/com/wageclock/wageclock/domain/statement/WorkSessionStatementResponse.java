package com.wageclock.wageclock.domain.statement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WorkSessionStatementResponse(Long id, LocalDateTime clockIn, LocalDateTime clockOut,
                                           BigDecimal hourlyWage, BigDecimal earnedAmount){
}
