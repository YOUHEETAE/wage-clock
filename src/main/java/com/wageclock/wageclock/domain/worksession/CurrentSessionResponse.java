package com.wageclock.wageclock.domain.worksession;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CurrentSessionResponse(Long sessionId, WorkSession.WorkSessionStatus status,
                                     BigDecimal hourlyWage, BigDecimal earnedAmount,
                                     LocalDateTime lastResumeAt) {}
