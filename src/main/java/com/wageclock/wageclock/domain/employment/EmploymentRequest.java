package com.wageclock.wageclock.domain.employment;

import java.math.BigDecimal;

public record EmploymentRequest(String workerEmail, BigDecimal hourlyWage, String employmentName){
}
