package com.wageclock.wageclock.domain.ewa;

import java.math.BigDecimal;

public record EwaRequestDto (Long sessionId, BigDecimal requestAmount, String idempotencyKey){
}
