package com.wageclock.wageclock.domain.EwaTransfer;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.CreateEmploymentRequest;
import com.wageclock.wageclock.domain.employment.CreateEmploymentResponse;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.ewa.EwaRequest;
import com.wageclock.wageclock.domain.ewa.EwaRequestDto;
import com.wageclock.wageclock.domain.ewa.EwaRequestRepository;
import com.wageclock.wageclock.domain.ewa.EwaResponseDto;
import com.wageclock.wageclock.domain.ewa.InitiateEwaResponse;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxRepository;
import com.wageclock.wageclock.domain.outbox.OutBoxScheduler;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.domain.worksession.ClockInRequest;
import com.wageclock.wageclock.domain.worksession.ClockInResponse;
import com.wageclock.wageclock.domain.worksession.ClockOutRequest;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import com.wageclock.wageclock.infrastructure.InterBankFailureNotification;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class EwaTransferIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16");
    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

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
    @Autowired EwaRequestRepository ewaRequestRepository;
    @Autowired EwaTransferRepository ewaTransferRepository;
    @Autowired EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    @Autowired PayPeriodRepository payPeriodRepository;
    @Autowired EwaTransferScheduler ewaTransferScheduler;
    @Autowired OutBoxScheduler outBoxScheduler;
    @MockitoBean WageTransferPort wageTransferPort;
    @MockitoBean VirtualAccountPort virtualAccountPort;

    private String workerToken;
    private String employerToken;
    private Long employmentId;

    @BeforeEach
    void setUp() throws InterruptedException {
        testRestTemplate.postForEntity("/api/auth/signup",
                new SignupRequest("김사장", "employer@test.com", "password", UserRole.EMPLOYER), Void.class);
        testRestTemplate.postForEntity("/api/auth/signup",
                new SignupRequest("박사원", "worker@test.com", "password", UserRole.WORKER), Void.class);

        employerToken = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("employer@test.com", "password", UserRole.EMPLOYER),
                LoginResponse.class).getBody().token();
        workerToken = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("worker@test.com", "password", UserRole.WORKER),
                LoginResponse.class).getBody().token();

        Long workerId = workerRepository.findByEmail("worker@test.com").get().getId();

        employmentId = testRestTemplate.postForEntity(
                "/api/employment",
                new HttpEntity<>(new CreateEmploymentRequest(workerId, BigDecimal.valueOf(3_600_000)), employerHeaders()),
                CreateEmploymentResponse.class).getBody().employmentId();

        Long sessionId = testRestTemplate.postForEntity(
                "/api/worksession/clockIn",
                new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders()),
                ClockInResponse.class).getBody().sessionId();
        Thread.sleep(2000);
        testRestTemplate.postForEntity("/api/worksession/clockOut",
                new HttpEntity<>(new ClockOutRequest(sessionId), workerHeaders()), Void.class);
    }

    @AfterEach
    void tearDown() {
        ewaTransferFailureOutBoxRepository.deleteAll();
        ewaTransferRepository.deleteAll();
        ewaRequestRepository.deleteAll();
        workSessionRepository.deleteAll();
        payPeriodRepository.deleteAll();
        employmentRepository.deleteAll();
        workerRepository.deleteAll();
        employerRepository.deleteAll();
    }

    private HttpHeaders workerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + workerToken);
        return headers;
    }

    private HttpHeaders employerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + employerToken);
        return headers;
    }

    private Long requestEwa(BigDecimal amount) {
        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewaRequest/request",
                new HttpEntity<>(new EwaRequestDto(employmentId, amount, UUID.randomUUID().toString()), workerHeaders()),
                EwaResponseDto.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().ewaRequestId();
    }

    private Long initiateEwa(Long ewaId) {
        testRestTemplate.postForEntity(
                "/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(null, employerHeaders()),
                InitiateEwaResponse.class);
        return ewaTransferRepository.findAll().get(0).getId();
    }

    // ─── 정상 이체 ───────────────────────────────────────────────────────────────

    @Test
    void initiateEwa_성공_EwaTransfer_COMPLETED_EwaRequest_APPROVED() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(null, employerHeaders()), InitiateEwaResponse.class);

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.COMPLETED, transfer.getStatus());
        assertEquals("TX-001", transfer.getTransferId());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(transfer.getAmount()));
    }

    // ─── VTIM (타행이체불능) ─────────────────────────────────────────────────────

    @Test
    void initiateEwa_VTIM_EwaTransfer_PENDING_INQUIRY() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult(null, "MSG-001", null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(null, employerHeaders()), InitiateEwaResponse.class);

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.PENDING, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.PENDING_INQUIRY, transfer.getStatus());
        assertEquals("MSG-001", transfer.getPendingMessageNo());
    }

    @Test
    void 스케줄러_VTIM_재조회_COMPLETED() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult(null, "MSG-001", null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(null, employerHeaders()), InitiateEwaResponse.class);

        assertEquals(EwaTransfer.EwaTransferStatus.PENDING_INQUIRY,
                ewaTransferRepository.findAll().get(0).getStatus());

        when(wageTransferPort.inquireTransfer(any()))
                .thenReturn(new WageTransferResult("TX-001", null, null, null));
        ewaTransferScheduler.retryPendingInquiryTransfer();

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.COMPLETED, transfer.getStatus());
        assertEquals("TX-001", transfer.getTransferId());
    }

    // ─── 예외 → UNKNOWN ──────────────────────────────────────────────────────────

    @Test
    void initiateEwa_예외발생_EwaTransfer_UNKNOWN() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(null, employerHeaders()), InitiateEwaResponse.class);

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.UNKNOWN, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.UNKNOWN, transfer.getStatus());
    }

    @Test
    void 스케줄러_UNKNOWN_재조회_COMPLETED() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(null, employerHeaders()), InitiateEwaResponse.class);

        assertEquals(EwaTransfer.EwaTransferStatus.UNKNOWN,
                ewaTransferRepository.findAll().get(0).getStatus());

        when(wageTransferPort.inquireTransfer(any()))
                .thenReturn(new WageTransferResult("TX-001", null, null, null));
        ewaTransferScheduler.retryPendingInquiryTransfer();

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());
        assertEquals(EwaTransfer.EwaTransferStatus.COMPLETED,
                ewaTransferRepository.findAll().get(0).getStatus());
    }

    // ─── 타행이체불능 소켓 수신 ──────────────────────────────────────────────────

    @Test
    void 타행이체불능_수신_FAILED_OutBox_생성() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        Long ewaTransferId = initiateEwa(ewaId);

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/mock/firm-banking/3000",
                new HttpEntity<>(new InterBankFailureNotification("TX-001", "EWA-" + ewaTransferId), new HttpHeaders()),
                Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.RETRYING, transfer.getStatus());

        List<EwaTransferFailureOutBoxEvent> events = ewaTransferFailureOutBoxRepository.findAll();
        assertEquals(1, events.size());
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PENDING, events.get(0).getStatus());
        assertEquals("TX-001", events.get(0).getTransferId());
    }

    // ─── OutBox 재이체 ────────────────────────────────────────────────────────────

    @Test
    void OutBox_재이체_성공_COMPLETED() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        Long ewaTransferId = initiateEwa(ewaId);

        testRestTemplate.postForEntity(
                "/mock/firm-banking/3000",
                new HttpEntity<>(new InterBankFailureNotification("TX-001", "EWA-" + ewaTransferId), new HttpHeaders()),
                Void.class);

        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PENDING,
                ewaTransferFailureOutBoxRepository.findAll().get(0).getStatus());

        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-002", null, null, null));
        outBoxScheduler.processEwaTransferFailureOutBoxEvent();

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.COMPLETED, transfer.getStatus());
        assertEquals("TX-002", transfer.getTransferId());

        EwaTransferFailureOutBoxEvent event = ewaTransferFailureOutBoxRepository.findAll().get(0);
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PROCESSED, event.getStatus());
    }

    @Test
    void OutBox_재이체_확정실패_FAILED_EwaRequest_APPROVED_유지() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        Long ewaTransferId = initiateEwa(ewaId);

        testRestTemplate.postForEntity(
                "/mock/firm-banking/3000",
                new HttpEntity<>(new InterBankFailureNotification("TX-001", "EWA-" + ewaTransferId), new HttpHeaders()),
                Void.class);

        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult(null, null, null, "계좌 없음"));
        outBoxScheduler.processEwaTransferFailureOutBoxEvent();

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.FAILED, transfer.getStatus());
    }

    @Test
    void OutBox_재이체_실패_retryCount_증가() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        Long ewaTransferId = initiateEwa(ewaId);

        testRestTemplate.postForEntity(
                "/mock/firm-banking/3000",
                new HttpEntity<>(new InterBankFailureNotification("TX-001", "EWA-" + ewaTransferId), new HttpHeaders()),
                Void.class);

        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("재이체 실패"));
        outBoxScheduler.processEwaTransferFailureOutBoxEvent();

        EwaTransferFailureOutBoxEvent event = ewaTransferFailureOutBoxRepository.findAll().get(0);
        assertEquals(1, event.getRetryCount());
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PENDING, event.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.UNKNOWN, transfer.getStatus());

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());
    }
}
