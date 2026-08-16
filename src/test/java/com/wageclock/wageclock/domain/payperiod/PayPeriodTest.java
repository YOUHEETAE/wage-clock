package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.employment.Employment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


@ExtendWith(MockitoExtension.class)
public class PayPeriodTest {
    @Mock
    Employment employment;

    private PayPeriod settlingPayPeriod() {
        PayPeriod payPeriod = new PayPeriod(employment);
        payPeriod.startSettling();
        return payPeriod;
    }

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

    @Test
    void startSettling_ACTIVE에서_SETTLING(){
        PayPeriod payPeriod = new PayPeriod(employment);

        payPeriod.startSettling();

        assertEquals(PayPeriod.PayPeriodStatus.SETTLING, payPeriod.getStatus());
    }

    @Test
    void startSettling_이미_SETTLING이면_예외(){
        PayPeriod payPeriod = settlingPayPeriod();

        assertThrows(IllegalStateException.class, payPeriod::startSettling);
    }

    @Test
    void reopen_SETTLING에서_ACTIVE(){
        PayPeriod payPeriod = settlingPayPeriod();

        payPeriod.reopen();

        assertEquals(PayPeriod.PayPeriodStatus.ACTIVE, payPeriod.getStatus());
    }

    @Test
    void reopen_ACTIVE면_예외(){
        PayPeriod payPeriod = new PayPeriod(employment);

        assertThrows(IllegalStateException.class, payPeriod::reopen);
    }

    @Test
    void close_SETTLING에서_CLOSED_periodEnd_기록(){
        PayPeriod payPeriod = settlingPayPeriod();

        payPeriod.close();

        assertEquals(PayPeriod.PayPeriodStatus.CLOSED, payPeriod.getStatus());
        assertEquals(LocalDate.now(), payPeriod.getPeriodEnd());
    }

    // 정산을 거치지 않은 마감을 막는다. 지급 없이 닫히면 적립액이 고아가 된다.
    @Test
    void close_ACTIVE면_예외(){
        PayPeriod payPeriod = new PayPeriod(employment);

        assertThrows(IllegalStateException.class, payPeriod::close);
    }

    @Test
    void close_이미_CLOSED면_예외(){
        PayPeriod payPeriod = settlingPayPeriod();
        payPeriod.close();

        assertThrows(IllegalStateException.class, payPeriod::close);
    }
}
