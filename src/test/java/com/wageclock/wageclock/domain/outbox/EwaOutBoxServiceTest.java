package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EwaOutBoxServiceTest {

    @Mock
    EwaOutBoxResultHandler ewaOutBoxResultHandler;

    @Mock
    VirtualAccountPort virtualAccountPort;

    @Mock
    EwaOutBoxEventRepository ewaOutBoxEventRepository;

    @InjectMocks
    EwaOutBoxService ewaOutBoxService;

    @Test
    void processEvent_성공_검증(){
        EwaOutBoxEvent event = EwaOutBoxEvent.builder()
                .portOnePaymentId("test-id")
                .ewaRequestId(1L)
                .amount(BigDecimal.valueOf(10000))
                .employerName("홍길동")
                .build();
        VirtualAccountResult account = new VirtualAccountResult("Toss", "1234", "2026-12-31");
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any())).thenReturn(account);
        ewaOutBoxService.processEvent(event);
        verify(ewaOutBoxResultHandler).saveSuccess(event, account);
    }
    @Test
    void processEvent_실패_검증(){
        EwaOutBoxEvent event = EwaOutBoxEvent.builder()
                .portOnePaymentId("test-id")
                .ewaRequestId(1L)
                .amount(BigDecimal.valueOf(10000))
                .employerName("홍길동")
                .build();
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("PortOne 호출 실패"));
        ewaOutBoxService.processEvent(event);
        verify(ewaOutBoxEventRepository).save(event);
        assertEquals(1, event.getRetryCount());
    }
}
