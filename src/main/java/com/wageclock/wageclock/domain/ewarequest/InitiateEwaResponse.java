package com.wageclock.wageclock.domain.ewarequest;

import java.math.BigDecimal;

public record InitiateEwaResponse(Long ewaRequestId, BigDecimal amount, EwaRequest.EwaRequestStatus status) {}
