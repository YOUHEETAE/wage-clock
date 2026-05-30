package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.employment.Employment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;


@ExtendWith(MockitoExtension.class)
public class PayPeriodTest {
    @Mock
    Employment employment;

    @Test
    void getRemainingEwaLimit_검증(){
        PayPeriod payPeriod = new PayPeriod(employment);
        payPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        payPeriod.addEwaAmount(BigDecimal.valueOf(1000));
        BigDecimal remainingEwaLimit = payPeriod.getRemainingEwaLimit();
        assertEquals(0, remainingEwaLimit.compareTo(BigDecimal.valueOf(2000)));
    }
    @Test
    void getRemainingEwaLimitWith_검증(){
        PayPeriod payPeriod = new PayPeriod(employment);
        payPeriod.addEarnedAmount(BigDecimal.valueOf(10000));
        payPeriod.addEwaAmount(BigDecimal.valueOf(1000));
        BigDecimal remainingEwaLimit = payPeriod.getRemainingEwaLimitWith(BigDecimal.valueOf(1000));
        assertEquals(0, remainingEwaLimit.compareTo(BigDecimal.valueOf(2300)));
    }
}
