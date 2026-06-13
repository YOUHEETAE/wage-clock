package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkSettlementServiceTest {

    @Mock BulkSettlementProcessor bulkSettlementProcessor;
    @Mock VirtualAccountPort virtualAccountPort;
    @Mock EmployerRepository employerRepository;
    @Mock WageTransferPort wageTransferPort;
    @Mock WorkerRepository workerRepository;
    BulkSettlementService bulkSettlementService;

    @BeforeEach
    void setUp() {
        bulkSettlementService = new BulkSettlementService(
                bulkSettlementProcessor, virtualAccountPort, employerRepository,
                wageTransferPort, workerRepository,
                Executors.newCachedThreadPool()
        );
    }

    @Test
    void initiateBulkSettlement_빈_컨텍스트_즉시완료() {
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of()));

        bulkSettlementService.initiateBulkSettlement("BULK-001");

        verify(bulkSettlementProcessor).completeSettlement("BULK-001");
        verify(wageTransferPort, never()).transfer(any(), any(), any());
    }

    @Test
    void initiateBulkSettlement_전체_성공_completeSettlement() {
        Worker worker = mock(Worker.class);
        when(worker.getId()).thenReturn(1L);
        BulkSettlementItemContext context = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, null, null);
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(context)));
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of(worker));
        when(wageTransferPort.transfer(worker, BigDecimal.valueOf(50000), 10L))
                .thenReturn(new WageTransferResult("TX-001", null));

        bulkSettlementService.initiateBulkSettlement("BULK-001");

        verify(bulkSettlementProcessor).assignTransferId(10L, "TX-001");
        verify(bulkSettlementProcessor).completeSettlement("BULK-001");
    }

    @Test
    void initiateBulkSettlement_VTIM_markPendingInquiry_failedSettlement() {
        Worker worker = mock(Worker.class);
        when(worker.getId()).thenReturn(1L);
        BulkSettlementItemContext context = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, null, null);
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(context)));
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of(worker));
        when(wageTransferPort.transfer(worker, BigDecimal.valueOf(50000), 10L))
                .thenReturn(new WageTransferResult(null, "MSG-001"));

        bulkSettlementService.initiateBulkSettlement("BULK-001");

        verify(bulkSettlementProcessor).markPendingInquiry(10L, "MSG-001");
        verify(bulkSettlementProcessor).failedSettlement("BULK-001");
    }

    @Test
    void initiateBulkSettlement_이체실패_markFailed_failedSettlement() {
        Worker worker = mock(Worker.class);
        when(worker.getId()).thenReturn(1L);
        BulkSettlementItemContext context = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, null, null);
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(context)));
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of(worker));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("이체 실패"));

        bulkSettlementService.initiateBulkSettlement("BULK-001");

        verify(bulkSettlementProcessor).markFailed(10L);
        verify(bulkSettlementProcessor).failedSettlement("BULK-001");
    }

    @Test
    void retrySettlement_PENDING_INQUIRY_성공_assignTransferId() {
        BulkSettlementItemContext inquiryContext = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, "TX-OLD", "MSG-001");
        when(bulkSettlementProcessor.loadPendingInquiryContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(inquiryContext)));
        when(wageTransferPort.inquireTransfer("MSG-001", 10L))
                .thenReturn(new WageTransferResult("TX-001", null));
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of()));

        bulkSettlementService.retrySettlement("BULK-001");

        verify(bulkSettlementProcessor).assignTransferId(10L, "TX-001");
    }

    @Test
    void retrySettlement_PENDING_INQUIRY_VTIM_markPendingInquiry() {
        BulkSettlementItemContext inquiryContext = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, "TX-OLD", "MSG-001");
        when(bulkSettlementProcessor.loadPendingInquiryContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(inquiryContext)));
        when(wageTransferPort.inquireTransfer("MSG-001", 10L))
                .thenReturn(new WageTransferResult(null, "MSG-002"));
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of()));

        bulkSettlementService.retrySettlement("BULK-001");

        verify(bulkSettlementProcessor).markPendingInquiry(10L, "MSG-002");
    }

    @Test
    void retrySettlement_PENDING_INQUIRY_실패_markFailed() {
        BulkSettlementItemContext inquiryContext = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, "TX-OLD", "MSG-001");
        when(bulkSettlementProcessor.loadPendingInquiryContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(inquiryContext)));
        when(wageTransferPort.inquireTransfer("MSG-001", 10L))
                .thenThrow(new RuntimeException("조회 실패"));
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of()));

        bulkSettlementService.retrySettlement("BULK-001");

        verify(bulkSettlementProcessor).markFailed(10L);
    }
}
