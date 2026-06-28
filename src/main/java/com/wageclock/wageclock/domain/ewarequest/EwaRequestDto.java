package com.wageclock.wageclock.domain.ewarequest;

import java.math.BigDecimal;

public record EwaRequestDto (Long employmentId, BigDecimal requestAmount, String idempotencyKey){
}
