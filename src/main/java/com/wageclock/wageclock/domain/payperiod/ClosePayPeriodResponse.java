package com.wageclock.wageclock.domain.payperiod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClosePayPeriodResponse (LocalDate periodStart, LocalDate periodEnd,
                                      BigDecimal totalEarnedAmount, BigDecimal totalEwaAmount,
                                      BigDecimal actualPayAmount){}
