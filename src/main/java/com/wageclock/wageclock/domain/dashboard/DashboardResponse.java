package com.wageclock.wageclock.domain.dashboard;

import com.wageclock.wageclock.domain.worksession.WorkSession;

import java.math.BigDecimal;

public record DashboardResponse (Long employmentId, Long workerId, String workerName, BigDecimal todayEarnedAmount,
                                 BigDecimal todayEwaAmount, WorkSession.WorkSessionStatus status){
}
