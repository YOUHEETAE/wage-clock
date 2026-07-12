package com.wageclock.wageclock.domain.ewarequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EwaRequestDetailResponse (Long ewaRequestId, BigDecimal requestAmount,
                                        EwaRequest.EwaRequestStatus status, LocalDateTime createdAt,
                                        LocalDateTime updatedAt){
}
