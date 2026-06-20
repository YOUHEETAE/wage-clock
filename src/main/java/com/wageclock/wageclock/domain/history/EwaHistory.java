package com.wageclock.wageclock.domain.history;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EwaHistory (Long ewaRequestId, BigDecimal requestedAmount,
                          EwaRequest.EwaRequestStatus status, LocalDateTime createdAt) implements HistoryPayload{
}
