package com.wageclock.wageclock.domain.ewa;

import java.math.BigDecimal;

public record EwaResponseDto (Long ewaRequestId, BigDecimal requestAmount, EwaRequest.EwaRequestStatus status) {
}
