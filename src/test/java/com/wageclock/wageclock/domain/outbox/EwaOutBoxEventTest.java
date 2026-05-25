package com.wageclock.wageclock.domain.outbox;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EwaOutBoxEventTest {

    @Test
    void MAX_RETRY_도달_시_FAILED_상태로_변경(){
        EwaOutBoxEvent ewaOutBoxEvent = EwaOutBoxEvent.builder()
                .portOnePaymentId("test-id")
                .ewaRequestId(1L)
                .amount(BigDecimal.valueOf(10000))
                .employerName("홍길동").build();
        assertEquals(EwaOutBoxEvent.OutBoxStatus.PENDING, ewaOutBoxEvent.getStatus());
        for(int i = 0; i < 5; i++){
            ewaOutBoxEvent.incrementRetryCount();
        }
        assertEquals(EwaOutBoxEvent.OutBoxStatus.FAILED,ewaOutBoxEvent.getStatus());
    }
    @Test
    void processed_호출_시_PROCESSED_상태로_변경(){
        EwaOutBoxEvent ewaOutBoxEvent = EwaOutBoxEvent.builder()
                .portOnePaymentId("test-id")
                .ewaRequestId(1L)
                .amount(BigDecimal.valueOf(10000))
                .employerName("홍길동").build();
        assertEquals(EwaOutBoxEvent.OutBoxStatus.PENDING, ewaOutBoxEvent.getStatus());
        ewaOutBoxEvent.processed();
        assertEquals(EwaOutBoxEvent.OutBoxStatus.PROCESSED,ewaOutBoxEvent.getStatus());
    }
}
