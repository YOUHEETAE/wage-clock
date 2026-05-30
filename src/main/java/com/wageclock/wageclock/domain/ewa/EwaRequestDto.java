package com.wageclock.wageclock.domain.ewa;

import java.math.BigDecimal;

public record EwaRequestDto (Long employmentId, BigDecimal requestAmount, String idempotencyKey){
}
