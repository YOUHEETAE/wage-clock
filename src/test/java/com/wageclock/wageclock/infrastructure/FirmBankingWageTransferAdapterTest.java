package com.wageclock.wageclock.infrastructure;

import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.global.exception.ExternalApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 헥토파이낸셜 응답 전문을 도메인 결과 타입으로 옮기는 매핑을 검증한다.
 * 전문 송수신(FirmBankingService)은 아직 스텁이지만 이 매핑은 실연동 후에도 유지되므로,
 * 스텁을 실제 소켓 통신으로 교체해도 이 테스트는 그대로 통과해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class FirmBankingWageTransferAdapterTest {

    @Mock FirmBankingService firmBankingService;
    @Mock Worker worker;
    @InjectMocks FirmBankingWageTransferAdapter adapter;

    // --- 2000/100 지급이체 ---

    @Test
    void 이체_응답코드_정상이면_SUCCESS() {
        when(firmBankingService.transfer(any(), any(), any()))
                .thenReturn(new HectoFinancialTransferResponse("0000", "1TX001"));

        WageTransferResult result = adapter.transfer(worker, BigDecimal.valueOf(50000), "1TX001");

        assertEquals(WageTransferResult.ResultType.SUCCESS, result.classify());
        assertEquals("1TX001", result.transferId());
    }

    @Test
    void 이체_VTIM이면_조회에_위임한다() {
        when(firmBankingService.transfer(any(), any(), any()))
                .thenReturn(new HectoFinancialTransferResponse("VTIM", "1TX001"));

        WageTransferResult result = adapter.transfer(worker, BigDecimal.valueOf(50000), "1TX001");

        assertEquals(WageTransferResult.ResultType.PENDING_INQUIRY, result.classify());
        assertEquals("1TX001", result.pendingMessageNo());
    }

    @Test
    void 이체_정상외_응답코드는_실패로_단정하지_않는다() {
        // 응답코드 리스트가 은행별로 상이해 접수 여부를 알 수 없으므로 조회로 넘긴다
        when(firmBankingService.transfer(any(), any(), any()))
                .thenReturn(new HectoFinancialTransferResponse("E001", "1TX001"));

        WageTransferResult result = adapter.transfer(worker, BigDecimal.valueOf(50000), "1TX001");

        assertEquals(WageTransferResult.ResultType.PENDING_INQUIRY, result.classify());
    }

    // --- 7000/100 이체결과조회 ---

    @Test
    void 조회_처리중이면_PENDING_INQUIRY() {
        when(firmBankingService.inquireTransferResult("1TX001"))
                .thenReturn(new HectoFinancialInquiryResponse("0000", "PRCS", "0", "0"));

        WageTransferResult result = adapter.inquireTransfer("1TX001");

        assertEquals(WageTransferResult.ResultType.PENDING_INQUIRY, result.classify());
        assertEquals("1TX001", result.pendingMessageNo());
    }

    @Test
    void 조회_처리결과_정상이면_SUCCESS() {
        when(firmBankingService.inquireTransferResult("1TX001"))
                .thenReturn(new HectoFinancialInquiryResponse("0000", "0000", "50000", "0"));

        WageTransferResult result = adapter.inquireTransfer("1TX001");

        assertEquals(WageTransferResult.ResultType.SUCCESS, result.classify());
        assertEquals("1TX001", result.transferId());
    }

    @Test
    void 조회_처리결과가_처리중도_정상도_아니면_FAILURE() {
        // 조회가 결론을 냈으므로 재시도 없이 실패로 확정한다
        when(firmBankingService.inquireTransferResult("1TX001"))
                .thenReturn(new HectoFinancialInquiryResponse("0000", "E021", "0", "50000"));

        WageTransferResult result = adapter.inquireTransfer("1TX001");

        assertEquals(WageTransferResult.ResultType.FAILURE, result.classify());
        assertEquals("E021", result.failureReason());
    }

    @Test
    void 조회_전문_자체가_실패하면_예외() {
        // 원 이체의 결과를 알 수 없는 상태다. 실패로 확정하면 돈이 나갔을 수도 있는 건의
        // 한도를 되돌리게 되므로, 예외로 던져 UNKNOWN 경로를 타야 한다.
        when(firmBankingService.inquireTransferResult("1TX001"))
                .thenReturn(new HectoFinancialInquiryResponse("E999", "0000", "0", "0"));

        assertThrows(ExternalApiException.class, () -> adapter.inquireTransfer("1TX001"));
    }
}
