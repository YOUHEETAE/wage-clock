package com.wageclock.wageclock.domain.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OutBoxSchedulerTest {
    @Mock
    EwaOutBoxEventRepository ewaOutBoxEventRepository;

    @Mock
    EwaOutBoxService ewaOutBoxService;

    @InjectMocks
    OutBoxScheduler outBoxScheduler;

    @Test
    void processEwaOutBoxEvent_검증(){
        EwaOutBoxEvent event = EwaOutBoxEvent.builder()
                .portOnePaymentId("test-id")
                .ewaRequestId(1L)
                .amount(BigDecimal.valueOf(10000))
                .employerName("홍길동")
                .build();
        when(ewaOutBoxEventRepository.findByStatus(any())).thenReturn(List.of(event));
        outBoxScheduler.processEwaOutBoxEvent();
        verify(ewaOutBoxEventRepository).findByStatus(any());
        verify(ewaOutBoxService).processEvent(any());
    }
}
