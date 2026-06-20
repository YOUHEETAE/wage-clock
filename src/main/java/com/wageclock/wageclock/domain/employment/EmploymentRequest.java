package com.wageclock.wageclock.domain.employment;

import java.math.BigDecimal;

public record EmploymentRequest(Long workerId, BigDecimal hourlyWage){
}
