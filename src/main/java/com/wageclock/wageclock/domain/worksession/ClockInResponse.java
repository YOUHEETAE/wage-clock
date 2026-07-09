package com.wageclock.wageclock.domain.worksession;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClockInResponse(Long sessionId, LocalDateTime clockIn, BigDecimal hourlyWage) {
}
