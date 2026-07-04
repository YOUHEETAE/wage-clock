package com.wageclock.wageclock.domain.dashboard;

import com.wageclock.wageclock.domain.worksession.WorkSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DashboardResponse(Long employmentId, Long workerId, String workerName,
                                BigDecimal todayEwaAmount,
                                WorkSession.WorkSessionStatus status,
                                BigDecimal hourlyWage, BigDecimal earnedAmount,
                                LocalDateTime lastResumeAt) {}
