package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.domain.payment.VirtualAccountResult;
import com.wageclock.wageclock.global.exception.ExternalApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PortOneVirtualAccountAdapterTest {

    @Mock
    PortOneService portOneService;

    @InjectMocks
    PortOneVirtualAccountAdapter portOneVirtualAccountAdaptor;

    @Test
    void portOneService_정상_호출(){
        when(portOneService.getVirtualAccountInfo("testId"))
                .thenReturn(new PortOneVirtualAccountInfoResponse("Ready", "1234",
                        new PortOneVirtualAccountInfoResponse
                                .Method("Toss", "1234", "2026-05-01","홍길동")));
        portOneVirtualAccountAdaptor.issueVirtualAccount("testId",
                BigDecimal.valueOf(10000), 1L, "홍길동");
        verify(portOneService).createVirtualAccount("testId",
                10000L, 1L, "홍길동");
        verify(portOneService).getVirtualAccountInfo("testId");
    }

    @Test
    void 반환_값_매핑_검증(){
        when(portOneService.getVirtualAccountInfo("testId"))
                .thenReturn(new PortOneVirtualAccountInfoResponse("Ready", "1234",
                        new PortOneVirtualAccountInfoResponse
                                .Method("Toss", "1234", "2026-05-01","홍길동")));
        VirtualAccountResult result = portOneVirtualAccountAdaptor.issueVirtualAccount("testId",
                BigDecimal.valueOf(10000), 1L, "홍길동");
        assertEquals("Toss",result.bank());
        assertEquals("1234",result.accountNumber());
        assertEquals("2026-05-01", result.expiredAt());
    }

    @Test
    void 예외_발생_시_전파(){
        when(portOneService.createVirtualAccount("testId",
                10000L, 1L, "홍길동")).thenThrow(ExternalApiException.class);
        assertThrows(ExternalApiException.class, () ->
                portOneVirtualAccountAdaptor.issueVirtualAccount("testId",
                        BigDecimal.valueOf(10000), 1L, "홍길동"));
    }
}
