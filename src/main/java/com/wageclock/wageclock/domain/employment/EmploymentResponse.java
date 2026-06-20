package com.wageclock.wageclock.domain.employment;

import java.math.BigDecimal;

public record CreateEmploymentResponse (Long employmentId, BigDecimal hourlyWage){
}
