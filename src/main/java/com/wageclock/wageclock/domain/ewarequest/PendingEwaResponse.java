package com.wageclock.wageclock.domain.ewarequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PendingEwaResponse(Long ewaRequestId, String workerName,
                                 BigDecimal requestedAmount, LocalDateTime createdAt){
}
