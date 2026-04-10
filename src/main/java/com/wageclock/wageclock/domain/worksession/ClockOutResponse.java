package com.wageclock.wageclock.domain.worksession;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClockOutResponse (LocalDateTime clockOut, BigDecimal earnedAmount){
}
