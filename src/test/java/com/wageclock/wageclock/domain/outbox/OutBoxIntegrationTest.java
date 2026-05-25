package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.CreateEmploymentRequest;
import com.wageclock.wageclock.domain.employment.CreateEmploymentResponse;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.ewa.*;
import com.wageclock.wageclock.domain.payment.PaymentRepository;
import com.wageclock.wageclock.domain.payment.PaymentScheduler;
import com.wageclock.wageclock.domain.payment.VirtualAccountPort;
import com.wageclock.wageclock.domain.payment.VirtualAccountResult;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.domain.worksession.ClockInRequest;
import com.wageclock.wageclock.domain.worksession.ClockInResponse;
import com.wageclock.wageclock.domain.worksession.ClockOutRequest;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class OutBoxIntegrationTest {
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
    @MockitoBean
    PaymentScheduler paymentScheduler;
    @Autowired
    EwaTransactionRepository ewaTransactionRepository;
    @Autowired
    EwaOutBoxEventRepository ewaOutBoxEventRepository;
    @Autowired
    OutBoxScheduler outBoxScheduler;

    @AfterEach
    void tearDown() {
        ewaTransactionRepository.deleteAll();
        ewaOutBoxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        ewaRequestRepository.deleteAll();
        workSessionRepository.deleteAll();
        employmentRepository.deleteAll();
        workerRepository.deleteAll();
        employerRepository.deleteAll();
    }

    String employerToken;
    String workerToken;
    Long sessionId;

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
        Long employmentId = employmentResponse.getBody().employmentId();

        HttpHeaders workerHeaders = new HttpHeaders();
        workerHeaders.set("Authorization", "Bearer " + workerToken);

        ResponseEntity<ClockInResponse> clockInResponse = testRestTemplate.postForEntity(
                "/api/worksession/clockIn",
                new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders),
                ClockInResponse.class);
        sessionId = clockInResponse.getBody().sessionId();

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
        EwaRequestDto requestDto = new EwaRequestDto(sessionId, amount, UUID.randomUUID().toString());
        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewaRequest/request",
                new HttpEntity<>(requestDto, workerHeaders()),
                EwaResponseDto.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().ewaRequestId();
    }
    @Test
    void initiateEwa_성공_시_outBoxEvent_PROCESSED(){
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-1234", "2026-05-24"));
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity(
                "/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(employerHeaders()),
                InitiateEwaResponse.class);
        EwaOutBoxEvent ewaOutBoxEvent = ewaOutBoxEventRepository.findAll().get(0);
        assertEquals(EwaOutBoxEvent.OutBoxStatus.PROCESSED, ewaOutBoxEvent.getStatus());
    }
    @Test
    void initiateEwa_실패_시_outBoxEvent_PENDING(){
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenThrow(new RuntimeException(""));
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity(
                "/api/ewaRequest/" + ewaId + "/initiateEwa",
                new HttpEntity<>(employerHeaders()),
                Void.class);
        EwaOutBoxEvent ewaOutBoxEvent = ewaOutBoxEventRepository.findAll().get(0);
        assertEquals(EwaOutBoxEvent.OutBoxStatus.PENDING, ewaOutBoxEvent.getStatus());
        assertEquals(0,ewaOutBoxEvent.getRetryCount());
        outBoxScheduler.processEwaOutBoxEvent();
        EwaOutBoxEvent retryEwaOutBoxEvent = ewaOutBoxEventRepository.findAll().get(0);
        assertEquals(1,retryEwaOutBoxEvent.getRetryCount());
    }
}
