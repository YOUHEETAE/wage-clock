package com.wageclock.wageclock.domain.history;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WorkSessionHistory (Long workSessionId, LocalDateTime clockIn,LocalDateTime clockOut,
                                  BigDecimal earnedAmount) implements HistoryPayload{
}
