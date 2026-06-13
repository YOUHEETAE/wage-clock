package com.wageclock.wageclock.domain.dashboard;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.CreateEmploymentRequest;
import com.wageclock.wageclock.domain.employment.CreateEmploymentResponse;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.ewa.EwaRequestRepository;
import com.wageclock.wageclock.domain.ewa.EwaTransactionRepository;
import com.wageclock.wageclock.domain.payment.PaymentRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
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
import org.springframework.http.HttpMethod;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class DashboardIntegrationTest {
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
    @Autowired
    PayPeriodRepository payPeriodRepository;
    @MockitoBean
    VirtualAccountPort virtualAccountPort;
    @Autowired
    EwaTransactionRepository ewaTransactionRepository;

    private String workerToken;
    private String employerToken;
    private Long employmentId;
    private Long employmentId2;
    private String workerToken2;


    @AfterEach
    void tearDown() {
        ewaTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        ewaRequestRepository.deleteAll();
        workSessionRepository.deleteAll();
        payPeriodRepository.deleteAll();
        employmentRepository.deleteAll();
        workerRepository.deleteAll();
        employerRepository.deleteAll();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        testRestTemplate.postForEntity("/api/auth/signup",
                new SignupRequest("김사장", "employer@test.com", "password", UserRole.EMPLOYER), Void.class);
        testRestTemplate.postForEntity("/api/auth/signup",
                new SignupRequest("박사원", "worker@test.com", "password", UserRole.WORKER), Void.class);
        testRestTemplate.postForEntity("/api/auth/signup",
                new SignupRequest("유사원", "worker2@test.com", "password", UserRole.WORKER), Void.class);

        employerToken = testRestTemplate.postForEntity("/api/auth/login",
                        new LoginRequest("employer@test.com", "password", UserRole.EMPLOYER), LoginResponse.class)
                .getBody().token();
        workerToken = testRestTemplate.postForEntity("/api/auth/login",
                        new LoginRequest("worker@test.com", "password", UserRole.WORKER), LoginResponse.class)
                .getBody().token();
        workerToken2 = testRestTemplate.postForEntity("/api/auth/login",
                        new LoginRequest("worker2@test.com", "password", UserRole.WORKER), LoginResponse.class)
                .getBody().token();

        Long workerId = workerRepository.findByEmail("worker@test.com").get().getId();
        Long workerId2 = workerRepository.findByEmail("worker2@test.com").get().getId();

        // 시급 3,600,000 → 1초당 1,000원 적립
        HttpHeaders employerHeaders = new HttpHeaders();
        employerHeaders.set("Authorization", "Bearer " + employerToken);
        ResponseEntity<CreateEmploymentResponse> employmentResponse = testRestTemplate.postForEntity(
                "/api/employment",
                new HttpEntity<>(new CreateEmploymentRequest(workerId, BigDecimal.valueOf(3_600_000)), employerHeaders),
                CreateEmploymentResponse.class);
        ResponseEntity<CreateEmploymentResponse> employment2Response = testRestTemplate.postForEntity(
                "/api/employment",
                new HttpEntity<>(new CreateEmploymentRequest(workerId2, BigDecimal.valueOf(3_600_000)), employerHeaders),
                CreateEmploymentResponse.class);
        this.employmentId = employmentResponse.getBody().employmentId();
        this.employmentId2 = employment2Response.getBody().employmentId();

        HttpHeaders workerHeaders = new HttpHeaders();
        workerHeaders.set("Authorization", "Bearer " + workerToken);

        ResponseEntity<ClockInResponse> clockInResponse = testRestTemplate.postForEntity(
                "/api/worksession/clockIn",
                new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders),
                ClockInResponse.class);
        HttpHeaders workerHeaders2 = new HttpHeaders();
        workerHeaders2.set("Authorization", "Bearer " + workerToken2);
        ResponseEntity<ClockInResponse> clockInResponse2 = testRestTemplate.postForEntity(
                "/api/worksession/clockIn",
                new HttpEntity<>(new ClockInRequest(employmentId2), workerHeaders2),
                ClockInResponse.class);
        Long sessionId = clockInResponse.getBody().sessionId();

        // 2초 대기 → 약 2,000원 적립 → 한도 약 600원
        Thread.sleep(2000);
        testRestTemplate.postForEntity("/api/worksession/clockOut",
                new HttpEntity<>(new ClockOutRequest(sessionId), workerHeaders), Void.class);
    }

    private HttpHeaders employerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + employerToken);
        return headers;
    }

    @Test
    void 직원_두명_대시보드_조회() {
        ResponseEntity<DashboardResponse[]> response = testRestTemplate.exchange(
                "/api/dashboard",
                HttpMethod.GET,
                new HttpEntity<>(null, employerHeaders()),
                DashboardResponse[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().length);
    }

    @Test
    void COMPLETED_세션_대시보드_조회() {
        ResponseEntity<DashboardResponse[]> response = testRestTemplate.exchange(
                "/api/dashboard",
                HttpMethod.GET,
                new HttpEntity<>(null, employerHeaders()),
                DashboardResponse[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        boolean hasCompleted = java.util.Arrays.stream(response.getBody())
                .anyMatch(r -> r.status() != null &&
                        r.status().name().equals("COMPLETED"));
        assertTrue(hasCompleted);
    }
}
