package com.wageclock.wageclock.domain.employment;

import java.math.BigDecimal;

public record EmploymentResponse(Long employmentId, BigDecimal hourlyWage){
}
