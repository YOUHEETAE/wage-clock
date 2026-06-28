package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.port.TransferType;
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
import static org.mockito.ArgumentMatchers.eq;
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
        BulkSettlementItemContext context = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, null);
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(context)));
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of(worker));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-001");
        when(wageTransferPort.transfer(eq(worker), eq(BigDecimal.valueOf(50000)), eq("TX-001")))
                .thenReturn(new WageTransferResult("TX-001", null, null));

        bulkSettlementService.initiateBulkSettlement("BULK-001");

        verify(bulkSettlementProcessor).assignMessageNo(10L, "TX-001");
        verify(bulkSettlementProcessor).completeItem(10L);
        verify(bulkSettlementProcessor).completeSettlement("BULK-001");
    }

    @Test
    void initiateBulkSettlement_VTIM_markPendingInquiry_failSettlement() {
        Worker worker = mock(Worker.class);
        when(worker.getId()).thenReturn(1L);
        BulkSettlementItemContext context = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, null);
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(context)));
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of(worker));
        when(wageTransferPort.transfer(eq(worker), eq(BigDecimal.valueOf(50000)), any()))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        bulkSettlementService.initiateBulkSettlement("BULK-001");

        verify(bulkSettlementProcessor).markPendingInquiry(10L);
        verify(bulkSettlementProcessor).transferFailSettlement("BULK-001");
    }

    @Test
    void initiateBulkSettlement_이체실패_failItem_failSettlement() {
        Worker worker = mock(Worker.class);
        when(worker.getId()).thenReturn(1L);
        BulkSettlementItemContext context = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, null);
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(context)));
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of(worker));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("이체 실패"));

        bulkSettlementService.initiateBulkSettlement("BULK-001");

        verify(bulkSettlementProcessor).unknownItem(10L);
        verify(bulkSettlementProcessor).transferFailSettlement("BULK-001");
    }

    @Test
    void initiateBulkSettlement_워커없음_failItem_failSettlement() {
        BulkSettlementItemContext context = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, null);
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(context)));
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of());

        bulkSettlementService.initiateBulkSettlement("BULK-001");

        verify(bulkSettlementProcessor).failItem(10L);
        verify(bulkSettlementProcessor).transferFailSettlement("BULK-001");
        verify(wageTransferPort, never()).transfer(any(), any(), any());
    }

    @Test
    void initiateBulkSettlement_prepareTransfer실패_Retryable_상태변경없음() {
        Worker worker = mock(Worker.class);
        when(worker.getId()).thenReturn(1L);
        BulkSettlementItemContext context = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, null);
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(context)));
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of(worker));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT))
                .thenThrow(new RuntimeException("Redis 장애"));

        bulkSettlementService.initiateBulkSettlement("BULK-001");

        verify(bulkSettlementProcessor, never()).completeItem(any());
        verify(bulkSettlementProcessor, never()).failItem(any());
        verify(bulkSettlementProcessor, never()).unknownItem(any());
        verify(bulkSettlementProcessor, never()).markPendingInquiry(any());
        verify(bulkSettlementProcessor).transferFailSettlement("BULK-001");
    }

    @Test
    void initiateBulkSettlement_모호한결과_unknownItem_failSettlement() {
        Worker worker = mock(Worker.class);
        when(worker.getId()).thenReturn(1L);
        BulkSettlementItemContext context = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, null);
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(context)));
        when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of(worker));
        when(wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT)).thenReturn("TX-001");
        when(wageTransferPort.transfer(eq(worker), eq(BigDecimal.valueOf(50000)), eq("TX-001")))
                .thenReturn(new WageTransferResult(null, null, null));

        bulkSettlementService.initiateBulkSettlement("BULK-001");

        verify(bulkSettlementProcessor).unknownItem(10L);
        verify(bulkSettlementProcessor).transferFailSettlement("BULK-001");
    }

    @Test
    void retrySettlement_PENDING_INQUIRY_성공_completeItem() {
        BulkSettlementItemContext inquiryContext = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, "MSG-001");
        when(bulkSettlementProcessor.loadPendingInquiryContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(inquiryContext)));
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult("TX-001", null, null));
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of()));

        bulkSettlementService.retrySettlement("BULK-001");

        verify(bulkSettlementProcessor).completeItem(10L);
    }

    @Test
    void retrySettlement_PENDING_INQUIRY_VTIM_markPendingInquiry() {
        BulkSettlementItemContext inquiryContext = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, "MSG-001");
        when(bulkSettlementProcessor.loadPendingInquiryContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(inquiryContext)));
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenReturn(new WageTransferResult(null, "MSG-002", null));
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of()));

        bulkSettlementService.retrySettlement("BULK-001");

        verify(bulkSettlementProcessor).markPendingInquiry(10L);
    }

    @Test
    void retrySettlement_PENDING_INQUIRY_예외_unknownItem() {
        BulkSettlementItemContext inquiryContext = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000), 10L, "MSG-001");
        when(bulkSettlementProcessor.loadPendingInquiryContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of(inquiryContext)));
        when(wageTransferPort.inquireTransfer("MSG-001"))
                .thenThrow(new RuntimeException("조회 실패"));
        when(bulkSettlementProcessor.loadItemContexts("BULK-001"))
                .thenReturn(new BulkSettlementContext(1L, List.of()));

        bulkSettlementService.retrySettlement("BULK-001");

        verify(bulkSettlementProcessor).unknownItem(10L);
    }
}