package com.wageclock.wageclock.domain.worksession;

import java.time.LocalDateTime;

public record ClockInResponse(Long sessionId, LocalDateTime clockIn) {
}
