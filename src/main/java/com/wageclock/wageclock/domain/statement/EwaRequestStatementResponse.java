package com.wageclock.wageclock.domain.statement;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EwaRequestStatementResponse(Long id, BigDecimal requestedAmount,
                                          EwaRequest.EwaRequestStatus status, LocalDateTime requestedAt){
}
