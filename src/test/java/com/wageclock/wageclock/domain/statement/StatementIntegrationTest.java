package com.wageclock.wageclock.domain.statement;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.outbox.BulkSettlementOutBoxEventRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.port.PaymentStatus;
import com.wageclock.wageclock.domain.port.VirtualAccountPaymentResult;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.settlement.BulkSettlement;
import com.wageclock.wageclock.domain.settlement.BulkSettlementRepository;
import com.wageclock.wageclock.infrastructure.PortOneWebhookPayload;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class StatementIntegrationTest extends IntegrationTestBase {

    @Autowired BulkSettlementRepository bulkSettlementRepository;
    @Autowired BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;

    private String employerToken;
    private String employerToken2;
    private Long employmentId;
    private Long payPeriodId;

    @AfterEach
    void tearDown() {
        bulkSettlementOutBoxEventRepository.deleteAll();
        bulkSettlementRepository.deleteAll();
        cleanCommon();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        signUp("김사장", "employer@statement-test.com", UserRole.EMPLOYER);
        signUp("이사장", "employer2@statement-test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@statement-test.com", UserRole.WORKER);
        employerToken = login("employer@statement-test.com");
        employerToken2 = login("employer2@statement-test.com");
        String workerToken = login("worker@statement-test.com");

        Long workplaceId = createWorkplace("테스트 사업장", employerToken);
        employmentId = createEmployment("worker@statement-test.com", BigDecimal.valueOf(10000), workplaceId, employerToken);
        Long sessionId = clockIn(employmentId, workerToken);
        Thread.sleep(1000);
        clockOut(sessionId, workerToken);

        settleAndClose();

        payPeriodId = payPeriodRepository
                .findByEmployment_IdAndStatus(employmentId, PayPeriod.PayPeriodStatus.CLOSED)
                .get().getId();
    }

    /**
     * 명세서는 마감된 PayPeriod를 전제로 한다. 지급 없이 닫는 경로를 없앴으므로
     * 일괄 정산을 끝까지 태워서 CLOSED를 만든다 — 이체 성공이 곧 마감이다.
     */
    private void settleAndClose() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null));

        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)), Void.class);

        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        when(virtualAccountPort.getPaymentResult(settlement.getPortOnePaymentId()))
                .thenReturn(new VirtualAccountPaymentResult(PaymentStatus.PAID, settlement.getTotalAmount()));
        testRestTemplate.postForEntity("/webhook",
                new HttpEntity<>(new PortOneWebhookPayload("Transaction.Paid", null,
                        new PortOneWebhookPayload.Data(null, settlement.getPortOnePaymentId(), null)),
                        new HttpHeaders()),
                Void.class);
    }

    @Test
    void 정산명세서_조회() {
        ResponseEntity<Map> response = testRestTemplate.exchange(
                "/api/statements/" + payPeriodId + "/pay-period",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().get("periodStart"));
        assertNotNull(response.getBody().get("periodEnd"));
        assertNotNull(response.getBody().get("totalEarnedAmount"));
        assertNotNull(response.getBody().get("totalEwaAmount"));
        assertNotNull(response.getBody().get("actualPayAmount"));
        assertEquals("박사원", response.getBody().get("workerName"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 워크세션_이력_조회() {
        ResponseEntity<List> response = testRestTemplate.exchange(
                "/api/statements/" + payPeriodId + "/work-sessions",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        Map<String, Object> session = (Map<String, Object>) response.getBody().get(0);
        assertNotNull(session.get("clockIn"));
        assertNotNull(session.get("clockOut"));
        assertNotNull(session.get("earnedAmount"));
        assertNotNull(session.get("hourlyWage"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void EWA_이력_조회_빈_리스트() {
        ResponseEntity<List> response = testRestTemplate.exchange(
                "/api/statements/" + payPeriodId + "/ewa-requests",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void 다른_고용주_접근_시_예외() {
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/statements/" + payPeriodId + "/pay-period",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken2)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
