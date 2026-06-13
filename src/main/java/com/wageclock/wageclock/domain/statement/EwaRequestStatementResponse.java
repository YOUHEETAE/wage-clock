package com.wageclock.wageclock.domain.statement;

import com.wageclock.wageclock.domain.ewa.EwaRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EwaRequestStatementResponse(Long id, BigDecimal requestedAmount,
                                          EwaRequest.EwaRequestStatus status, LocalDateTime requestedAt){
}
