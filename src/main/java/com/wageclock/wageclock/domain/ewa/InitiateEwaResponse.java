package com.wageclock.wageclock.domain.ewa;

import java.math.BigDecimal;

public record InitiateEwaResponse(Long ewaRequestId, BigDecimal amount, EwaRequest.EwaRequestStatus status,
                                  String accountNumber, String bank, String expiredAt) {}
