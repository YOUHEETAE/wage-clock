package com.wageclock.wageclock.domain.ewarequest;

import java.math.BigDecimal;

public record EwaResponseDto (Long ewaRequestId, BigDecimal requestAmount, EwaRequest.EwaRequestStatus status) {
}
