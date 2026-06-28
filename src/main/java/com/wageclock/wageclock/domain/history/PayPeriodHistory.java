package com.wageclock.wageclock.domain.history;

import com.wageclock.wageclock.domain.payperiod.PayPeriod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayPeriodHistory (Long payPeriodId, LocalDate payPeriodStart, LocalDate payPeriodEnd,
                                BigDecimal totalEarnedAmount, BigDecimal totalEwaAmount,
                                PayPeriod.PayPeriodStatus status) implements HistoryPayload{
}
