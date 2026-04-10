package com.wageclock.wageclock.domain.employment;

import java.math.BigDecimal;

public record CreateEmploymentRequest (Long workerId, BigDecimal hourlyWage){
}
