package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.EmploymentRequest;
import com.wageclock.wageclock.domain.employment.EmploymentResponse;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.outbox.*;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.domain.worksession.ClockInRequest;
import com.wageclock.wageclock.domain.worksession.ClockInResponse;
import com.wageclock.wageclock.domain.worksession.ClockOutRequest;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import com.wageclock.wageclock.infrastructure.InterBankFailureNotification;
import com.wageclock.wageclock.infrastructure.PortOneWebhookPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class BulkSettlementIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");
    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired TestRestTemplate testRestTemplate;
    @Autowired WorkerRepository workerRepository;
    @Autowired EmployerRepository employerRepository;
    @Autowired EmploymentRepository employmentRepository;
    @Autowired WorkSessionRepository workSessionRepository;
    @Autowired PayPeriodRepository payPeriodRepository;
    @Autowired BulkSettlementRepository bulkSettlementRepository;
    @Autowired BulkSettlementItemRepository bulkSettlementItemRepository;
    @Autowired BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;
    @Autowired InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    @Autowired OutBoxScheduler outBoxScheduler;
    @Autowired BulkSettlementScheduler bulkSettlementScheduler;
    @MockitoBean VirtualAccountPort virtualAccountPort;
    @MockitoBean WageTransferPort wageTransferPort;

    String employerToken;
    String workerToken;
    Long employmentId;

    @BeforeEach
    void setUp() throws InterruptedException {
        testRestTemplate.postForEntity("/api/auth/sign-up",
                new SignupRequest("김사장", "employer@test.com", "password", UserRole.EMPLOYER), Void.class);
        testRestTemplate.postForEntity("/api/auth/sign-up",
                new SignupRequest("박사원", "worker@test.com", "password", UserRole.WORKER), Void.class);
        employerToken = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("employer@test.com", "password"),
                LoginResponse.class).getBody().token();
        workerToken = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("worker@test.com", "password"),
                LoginResponse.class).getBody().token();
        Long workerId = workerRepository.findByEmail("worker@test.com").get().getId();

        HttpHeaders employerHeaders = employerHeaders();
        ResponseEntity<EmploymentResponse> employmentResponse = testRestTemplate.postForEntity(
                "/api/employments",
                new HttpEntity<>(new EmploymentRequest(workerId, BigDecimal.valueOf(3_600_000)), employerHeaders),
                EmploymentResponse.class);
        this.employmentId = employmentResponse.getBody().employmentId();

        ResponseEntity<ClockInResponse> clockInResponse = testRestTemplate.postForEntity(
                "/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders()),
                ClockInResponse.class);
        Long sessionId = clockInResponse.getBody().sessionId();
        Thread.sleep(2000);
        testRestTemplate.postForEntity("/api/work-sessions/clock-out",
                new HttpEntity<>(new ClockOutRequest(sessionId), workerHeaders()), Void.class);
    }

    @AfterEach
    void tearDown() {
        interBankFailureOutBoxEventRepository.deleteAll();
        bulkSettlementOutBoxEventRepository.deleteAll();
        bulkSettlementRepository.deleteAll();
        workSessionRepository.deleteAll();
        payPeriodRepository.deleteAll();
        employmentRepository.deleteAll();
        workerRepository.deleteAll();
        employerRepository.deleteAll();
    }
    private HttpHeaders employerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + employerToken);
        return headers;
    }

    private HttpHeaders workerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + workerToken);
        return headers;
    }

    private Long setupSecondWorker() throws InterruptedException {
        testRestTemplate.postForEntity("/api/auth/sign-up",
                new SignupRequest("이직원", "worker2@test.com", "password", UserRole.WORKER), Void.class);
        String workerToken2 = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("worker2@test.com", "password"),
                LoginResponse.class).getBody().token();
        Long workerId2 = workerRepository.findByEmail("worker2@test.com").get().getId();

        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("Authorization", "Bearer " + workerToken2);

        Long employmentId2 = testRestTemplate.postForEntity(
                "/api/employments",
                new HttpEntity<>(new EmploymentRequest(workerId2, BigDecimal.valueOf(3_600_000)), employerHeaders()),
                EmploymentResponse.class).getBody().employmentId();

        Long sessionId2 = testRestTemplate.postForEntity(
                "/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId2), headers2),
                ClockInResponse.class).getBody().sessionId();
        Thread.sleep(2000);
        testRestTemplate.postForEntity("/api/work-sessions/clock-out",
                new HttpEntity<>(new ClockOutRequest(sessionId2), headers2), Void.class);
        return employmentId2;
    }

    private void requestAndTriggerSettlement(List<Long> employmentIds) {
        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(employmentIds, employerHeaders()),
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
                new HttpEntity<>(List.of(employmentId), employerHeaders()),
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
                new HttpEntity<>(List.of(employmentId), employerHeaders()),
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
                new HttpEntity<>(List.of(employmentId), employerHeaders()),
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
                new HttpEntity<>(List.of(employmentId), employerHeaders()),
                BulkSettlementResponse.class);
        String portOnePaymentId = bulkSettlementRepository.findAll().get(0).getPortOnePaymentId();

        testRestTemplate.postForEntity("/webhook",
                new HttpEntity<>(new PortOneWebhookPayload("Transaction.Paid", null,
                        new PortOneWebhookPayload.Data(null, portOnePaymentId, null)), new HttpHeaders()),
                Void.class);

        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED,
                bulkSettlementRepository.findAll().get(0).getStatus());

        // 3000/100 타행이체불능 수신
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

        // 재이체 성공
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