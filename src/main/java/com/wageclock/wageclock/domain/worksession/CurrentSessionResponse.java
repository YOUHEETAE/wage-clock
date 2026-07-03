package com.wageclock.wageclock.domain.worksession;

public record CurrentSessionResponse(Long sessionId, WorkSession.WorkSessionStatus status) {}
