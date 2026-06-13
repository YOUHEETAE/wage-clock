package com.wageclock.wageclock.domain.payment;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.CreateEmploymentRequest;
import com.wageclock.wageclock.domain.employment.CreateEmploymentResponse;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.ewa.*;
import com.wageclock.wageclock.domain.outbox.EwaOutBoxEventRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.domain.worksession.*;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class PaymentIntegrationTest {

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

    @Autowired
    TestRestTemplate testRestTemplate;
    @Autowired
    WorkerRepository workerRepository;
    @Autowired
    EmployerRepository employerRepository;
    @Autowired
    EmploymentRepository employmentRepository;
    @Autowired
    WorkSessionRepository workSessionRepository;
    @Autowired
    EwaRequestRepository ewaRequestRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @MockitoBean
    VirtualAccountPort virtualAccountPort;
    @Autowired
    PaymentScheduler paymentScheduler;
    @Autowired
    EwaTransactionRepository ewaTransactionRepository;
    @Autowired
    EwaOutBoxEventRepository ewaOutBoxEventRepository;
    @Autowired
    PayPeriodRepository payPeriodRepository;

    @AfterEach
    void tearDown() {
        ewaTransactionRepository.deleteAll();
        ewaOutBoxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        ewaRequestRepository.deleteAll();
        workSessionRepository.deleteAll();
        payPeriodRepository.deleteAll();
        employmentRepository.deleteAll();
        workerRepository.deleteAll();
        employerRepository.deleteAll();
    }

    String employerToken;
    String workerToken;
    Long employmentId;

    @BeforeEach
    void setUp() throws InterruptedException {
        testRestTemplate.postForEntity("/api/auth/signup",
                new SignupRequest("김사장", "employer@test.com", "password", UserRole.EMPLOYER)
                , Void.class);
        testRestTemplate.postForEntity("/api/auth/signup",
                new SignupRequest("박사원", "worker@test.com", "password", UserRole.WORKER),
                Void.class);
        employerToken = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("employer@test.com", "password", UserRole.EMPLOYER)
                , LoginResponse.class).getBody().token();
        workerToken = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("worker@test.com", "password", UserRole.WORKER)
                , LoginResponse.class).getBody().token();
        Long workerId = workerRepository.findByEmail("worker@test.com").get().getId();

        // 시급 3,600,000 → 1초당 1,000원 적립
        HttpHeaders employerHeaders = new HttpHeaders();
        employerHeaders.set("Authorization", "Bearer " + employerToken);
        ResponseEntity<CreateEmploymentResponse> employmentResponse = testRestTemplate.postForEntity(
                "/api/employment",
                new HttpEntity<>(new CreateEmploymentRequest(workerId, BigDecimal.valueOf(3_600_000)), employerHeaders),
                CreateEmploymentResponse.class);
        this.employmentId = employmentResponse.getBody().employmentId();

        HttpHeaders workerHeaders = new HttpHeaders();
        workerHeaders.set("Authorization", "Bearer " + workerToken);

        ResponseEntity<ClockInResponse> clockInResponse = testRestTemplate.postForEntity(
                "/api/worksession/clockIn",
                new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders),
                ClockInResponse.class);
        Long sessionId = clockInResponse.getBody().sessionId();

        // 2초 대기 → 약 2,000원 적립 → 한도 약 600원
        Thread.sleep(2000);

        testRestTemplate.postForEntity(
                "/api/worksession/clockOut",
                new HttpEntity<>(new ClockOutRequest(sessionId), workerHeaders),
                Void.class);
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
        EwaRequestDto requestDto = new EwaRequestDto(employmentId, amount, UUID.randomUUID().toString());
        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewaRequest/request",
                new HttpEntity<>(requestDto, workerHeaders()),
                EwaResponseDto.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().ewaRequestId();
    }

    @Test
    void 승인_시_payment_저장_및_ewa_status_APPROVE(){
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234", "2026-05-05"));
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        ResponseEntity<InitiateEwaResponse> response = testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(employerHeaders()), InitiateEwaResponse.class);
        String portOnePaymentId = paymentRepository.findAll().get(0).getPortOnePaymentId();
        ResponseEntity<Void> webhookResponse = testRestTemplate.postForEntity(
                "/webhook",
                new PortOneWebhookPayload("Transaction.Paid", "",
                        new PortOneWebhookPayload.Data("", portOnePaymentId, "")), Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Payment> payment = paymentRepository.findAllWithHistories();
        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).orElseThrow();
        assertEquals(1, payment.size());
        assertEquals(3, payment.get(0).getHistories().size());
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.get(0).getStatus());
        assertEquals(Payment.PaymentStatus.READY, payment.get(0).getHistories().get(0).getStatus());
        assertEquals(Payment.PaymentStatus.PROCESSING, payment.get(0).getHistories().get(1).getStatus());
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.get(0).getHistories().get(2).getStatus());
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());
    }
    @Test
    void 거절_시_EwaRequest_REJECTED_및_EwaAmount_감소(){
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/reject",
                new HttpEntity<>(employerHeaders()), EwaResponseDto.class);
        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).orElseThrow();
        PayPeriod payPeriod = payPeriodRepository.findById(employmentId).orElseThrow();
        assertEquals(EwaRequest.EwaRequestStatus.REJECTED, ewaRequest.getStatus());
        assertEquals(0, payPeriod.getTotalEwaAmount().compareTo(BigDecimal.ZERO));
    }
    @Test
    void 실패_시_payment_저장_및_ewa_status_FAILED(){
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234", "2026-05-05"));
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        ResponseEntity<InitiateEwaResponse> response = testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(employerHeaders()), InitiateEwaResponse.class);
        String portOnePaymentId = paymentRepository.findAll().get(0).getPortOnePaymentId();
        ResponseEntity<Void> webhookResponse = testRestTemplate.postForEntity(
                "/webhook",
                new PortOneWebhookPayload("Transaction.Failed", "",
                        new PortOneWebhookPayload.Data("", portOnePaymentId, "")), Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Payment> payment = paymentRepository.findAllWithHistories();
        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).orElseThrow();
        assertEquals(1, payment.size());
        assertEquals(3, payment.get(0).getHistories().size());
        assertEquals(Payment.PaymentStatus.FAILED, payment.get(0).getStatus());
        assertEquals(Payment.PaymentStatus.READY, payment.get(0).getHistories().get(0).getStatus());
        assertEquals(Payment.PaymentStatus.PROCESSING, payment.get(0).getHistories().get(1).getStatus());
        assertEquals(Payment.PaymentStatus.FAILED, payment.get(0).getHistories().get(2).getStatus());
        assertEquals(EwaRequest.EwaRequestStatus.FAILED, ewaRequest.getStatus());
    }
    @Test
    void 스케줄러_PROCESSING_PAID_처리(){
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234", "2026-05-05"));
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        ResponseEntity<InitiateEwaResponse> response = testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(employerHeaders()), InitiateEwaResponse.class);
        String portOnePaymentId = paymentRepository.findAll().get(0).getPortOnePaymentId();
        when(virtualAccountPort.getVirtualAccountStatus(portOnePaymentId)).thenReturn("PAID");
        paymentScheduler.retryPayment();
        List<Payment> payment = paymentRepository.findAllWithHistories();
        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).orElseThrow();
        assertEquals(1, payment.size());
        assertEquals(3, payment.get(0).getHistories().size());
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.get(0).getStatus());
        assertEquals(Payment.PaymentStatus.READY, payment.get(0).getHistories().get(0).getStatus());
        assertEquals(Payment.PaymentStatus.PROCESSING, payment.get(0).getHistories().get(1).getStatus());
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.get(0).getHistories().get(2).getStatus());
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());
    }
    @Test
    void 스케줄러_PROCESSING_FAILED_처리(){
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234", "2026-05-05"));
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        ResponseEntity<InitiateEwaResponse> response = testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(employerHeaders()), InitiateEwaResponse.class);
        String portOnePaymentId = paymentRepository.findAll().get(0).getPortOnePaymentId();
        when(virtualAccountPort.getVirtualAccountStatus(portOnePaymentId)).thenReturn("FAILED");
        paymentScheduler.retryPayment();
        List<Payment> payment = paymentRepository.findAllWithHistories();
        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).orElseThrow();
        assertEquals(1, payment.size());
        assertEquals(3, payment.get(0).getHistories().size());
        assertEquals(Payment.PaymentStatus.FAILED, payment.get(0).getStatus());
        assertEquals(Payment.PaymentStatus.READY, payment.get(0).getHistories().get(0).getStatus());
        assertEquals(Payment.PaymentStatus.PROCESSING, payment.get(0).getHistories().get(1).getStatus());
        assertEquals(Payment.PaymentStatus.FAILED, payment.get(0).getHistories().get(2).getStatus());
        assertEquals(EwaRequest.EwaRequestStatus.FAILED, ewaRequest.getStatus());
    }
}
