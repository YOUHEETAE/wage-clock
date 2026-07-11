package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.outbox.*;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.infrastructure.InterBankFailureNotification;
import com.wageclock.wageclock.infrastructure.PortOneWebhookPayload;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class BulkSettlementIntegrationTest extends IntegrationTestBase {

    @Autowired BulkSettlementRepository bulkSettlementRepository;
    @Autowired BulkSettlementItemRepository bulkSettlementItemRepository;
    @Autowired BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;
    @Autowired InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    @Autowired OutBoxScheduler outBoxScheduler;
    @Autowired BulkSettlementScheduler bulkSettlementScheduler;

    String employerToken;
    String workerToken;
    Long employmentId;
    Long workplaceId;

    @BeforeEach
    void setUp() throws InterruptedException {
        signUp("김사장", "employer@bulk-test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@bulk-test.com", UserRole.WORKER);
        employerToken = login("employer@bulk-test.com");
        workerToken = login("worker@bulk-test.com");
        workplaceId = createWorkplace("테스트 사업장", employerToken);
        employmentId = createEmployment("worker@bulk-test.com", BigDecimal.valueOf(3_600_000), workplaceId, employerToken);
        Long sessionId = clockIn(employmentId, workerToken);
        Thread.sleep(2000);
        clockOut(sessionId, workerToken);
    }

    @AfterEach
    void tearDown() {
        interBankFailureOutBoxEventRepository.deleteAll();
        bulkSettlementOutBoxEventRepository.deleteAll();
        bulkSettlementRepository.deleteAll();
        cleanCommon();
    }

    private Long setupSecondWorker() throws InterruptedException {
        signUp("이직원", "worker2@test.com", UserRole.WORKER);
        String workerToken2 = login("worker2@test.com");
        Long employmentId2 = createEmployment("worker2@test.com", BigDecimal.valueOf(3_600_000), workplaceId, employerToken);
        Long sessionId2 = clockIn(employmentId2, workerToken2);
        Thread.sleep(2000);
        clockOut(sessionId2, workerToken2);
        return employmentId2;
    }

    private void requestAndTriggerSettlement(List<Long> employmentIds) {
        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(employmentIds, authHeaders(employerToken)),
                BulkSettlementResponse.class);
        String portOnePaymentId = bulkSettlementRepository.findAll().get(0).getPortOnePaymentId();
        testRestTemplate.postForEntity("/webhook",
                new HttpEntity<>(new PortOneWebhookPayload("Transaction.Paid", null,
                        new PortOneWebhookPayload.Data(null, portOnePaymentId, null)), new HttpHeaders()),
                Void.class);
    }

    @Test
    void bulkSettlementRequest_정상_BulkSettlement_PROCESSING_Outbox_PROCESSED() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));

        ResponseEntity<BulkSettlementResponse> response = testRestTemplate.postForEntity(
                "/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)),
                BulkSettlementResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.PROCESSING, settlement.getStatus());
        BulkSettlementOutBoxEvent event = bulkSettlementOutBoxEventRepository.findAll().get(0);
        assertEquals(BulkSettlementOutBoxEvent.OutBoxStatus.PROCESSED, event.getStatus());
    }

    @Test
    void initiateBulkSettlement_성공_아이템_COMPLETED_PayPeriod_CLOSED_BulkSettlement_COMPLETED() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null));

        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)),
                BulkSettlementResponse.class);
        String portOnePaymentId = bulkSettlementRepository.findAll().get(0).getPortOnePaymentId();

        testRestTemplate.postForEntity("/webhook",
                new HttpEntity<>(new PortOneWebhookPayload("Transaction.Paid", null,
                        new PortOneWebhookPayload.Data(null, portOnePaymentId, null)), new HttpHeaders()),
                Void.class);

        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED, settlement.getStatus());
        BulkSettlementItem item = bulkSettlementItemRepository.findAll().get(0);
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.COMPLETED, item.getStatus());
        PayPeriod payPeriod = payPeriodRepository.findAll().get(0);
        assertEquals(PayPeriod.PayPeriodStatus.CLOSED, payPeriod.getStatus());
    }

    @Test
    void initiateBulkSettlement_VTIM_아이템_PENDING_INQUIRY_BulkSettlement_TRANSFER_FAILED() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)),
                BulkSettlementResponse.class);
        String portOnePaymentId = bulkSettlementRepository.findAll().get(0).getPortOnePaymentId();

        testRestTemplate.postForEntity("/webhook",
                new HttpEntity<>(new PortOneWebhookPayload("Transaction.Paid", null,
                        new PortOneWebhookPayload.Data(null, portOnePaymentId, null)), new HttpHeaders()),
                Void.class);

        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.TRANSFER_FAILED, settlement.getStatus());
        BulkSettlementItem item = bulkSettlementItemRepository.findAll().get(0);
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY, item.getStatus());
    }

    @Test
    void retrySettlement_PENDING_INQUIRY_조회성공_COMPLETED() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        requestAndTriggerSettlement(List.of(employmentId));

        assertEquals(BulkSettlement.BulkSettlementStatus.TRANSFER_FAILED,
                bulkSettlementRepository.findAll().get(0).getStatus());

        when(wageTransferPort.inquireTransfer(any()))
                .thenReturn(new WageTransferResult("TX-001", null, null));
        bulkSettlementScheduler.retryFailedTransfers();

        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED, settlement.getStatus());
        BulkSettlementItem item = bulkSettlementItemRepository.findAll().get(0);
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.COMPLETED, item.getStatus());
    }

    @Test
    void initiateBulkSettlement_다수_아이템_일부_성공_일부_애매함() throws InterruptedException {
        Long employmentId2 = setupSecondWorker();

        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null))
                .thenThrow(new RuntimeException("이체 실패"));

        requestAndTriggerSettlement(List.of(employmentId, employmentId2));

        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.TRANSFER_FAILED, settlement.getStatus());

        List<BulkSettlementItem> items = bulkSettlementItemRepository.findAll();
        assertEquals(2, items.size());
        long completedCount = items.stream()
                .filter(i -> i.getStatus() == BulkSettlementItem.BulkSettlementItemStatus.COMPLETED).count();
        long unknownCount = items.stream()
                .filter(i -> i.getStatus() == BulkSettlementItem.BulkSettlementItemStatus.UNKNOWN).count();
        assertEquals(1, completedCount);
        assertEquals(1, unknownCount);
    }

    @Test
    void receiveInterBankFailure_아이템_RETRYING_세틀먼트_RETRYING_Outbox_생성_재이체_성공() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.prepareTransfer(any())).thenReturn("2TX001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("2TX001", null, null));

        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)),
                BulkSettlementResponse.class);
        String portOnePaymentId = bulkSettlementRepository.findAll().get(0).getPortOnePaymentId();

        testRestTemplate.postForEntity("/webhook",
                new HttpEntity<>(new PortOneWebhookPayload("Transaction.Paid", null,
                        new PortOneWebhookPayload.Data(null, portOnePaymentId, null)), new HttpHeaders()),
                Void.class);

        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED,
                bulkSettlementRepository.findAll().get(0).getStatus());

        ResponseEntity<Void> response = testRestTemplate.postForEntity("/mock/firm-banking/3000",
                new HttpEntity<>(new InterBankFailureNotification("2TX001"), new HttpHeaders()),
                Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        BulkSettlementItem item = bulkSettlementItemRepository.findAll().get(0);
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.RETRYING, item.getStatus());
        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.RETRYING, settlement.getStatus());
        InterBankFailureOutBoxEvent event = interBankFailureOutBoxEventRepository.findAll().get(0);
        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.PENDING, event.getStatus());
        assertEquals("2TX001", event.getMessageNo());

        when(wageTransferPort.prepareTransfer(any())).thenReturn("2TX002");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("2TX002", null, null));
        outBoxScheduler.processInterBankFailureOutBoxEvent();

        BulkSettlementItem retried = bulkSettlementItemRepository.findAll().get(0);
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.COMPLETED, retried.getStatus());
        BulkSettlement settledAgain = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED, settledAgain.getStatus());
        InterBankFailureOutBoxEvent processed = interBankFailureOutBoxEventRepository.findAll().get(0);
        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.PROCESSED, processed.getStatus());
    }
}
