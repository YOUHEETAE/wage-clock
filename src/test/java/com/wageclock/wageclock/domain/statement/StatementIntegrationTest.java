package com.wageclock.wageclock.domain.statement;

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
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
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
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class StatementIntegrationTest {

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
    @Autowired EwaTransactionRepository ewaTransactionRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PayPeriodRepository payPeriodRepository;
    @MockitoBean VirtualAccountPort virtualAccountPort;

    private String employerToken;
    private String employerToken2;
    private Long employmentId;
    private Long payPeriodId;

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
                new SignupRequest("이사장", "employer2@test.com", "password", UserRole.EMPLOYER), Void.class);
        testRestTemplate.postForEntity("/api/auth/signup",
                new SignupRequest("박사원", "worker@test.com", "password", UserRole.WORKER), Void.class);

        employerToken = testRestTemplate.postForEntity("/api/auth/login",
                        new LoginRequest("employer@test.com", "password", UserRole.EMPLOYER), LoginResponse.class)
                .getBody().token();
        employerToken2 = testRestTemplate.postForEntity("/api/auth/login",
                        new LoginRequest("employer2@test.com", "password", UserRole.EMPLOYER), LoginResponse.class)
                .getBody().token();
        String workerToken = testRestTemplate.postForEntity("/api/auth/login",
                        new LoginRequest("worker@test.com", "password", UserRole.WORKER), LoginResponse.class)
                .getBody().token();

        Long workerId = workerRepository.findByEmail("worker@test.com").get().getId();
        HttpHeaders employerHeaders = new HttpHeaders();
        employerHeaders.set("Authorization", "Bearer " + employerToken);
        ResponseEntity<CreateEmploymentResponse> empResponse = testRestTemplate.postForEntity(
                "/api/employment",
                new HttpEntity<>(new CreateEmploymentRequest(workerId, BigDecimal.valueOf(10000)), employerHeaders),
                CreateEmploymentResponse.class);
        employmentId = empResponse.getBody().employmentId();

        HttpHeaders workerHeaders = new HttpHeaders();
        workerHeaders.set("Authorization", "Bearer " + workerToken);
        ResponseEntity<ClockInResponse> clockInResponse = testRestTemplate.postForEntity(
                "/api/worksession/clockIn",
                new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders),
                ClockInResponse.class);
        Long sessionId = clockInResponse.getBody().sessionId();
        Thread.sleep(1000);
        testRestTemplate.postForEntity("/api/worksession/clockOut",
                new HttpEntity<>(new ClockOutRequest(sessionId), workerHeaders), Void.class);

        testRestTemplate.postForEntity(
                "/api/payperiod/" + employmentId + "/close",
                new HttpEntity<>(null, employerHeaders), Void.class);

        payPeriodId = payPeriodRepository
                .findByEmploymentIdAndStatus(employmentId, PayPeriod.PayPeriodStatus.CLOSED)
                .get().getId();
    }

    private HttpHeaders authHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    @Test
    void 정산명세서_조회() {
        ResponseEntity<Map> response = testRestTemplate.exchange(
                "/api/statement/" + payPeriodId + "/payperiod",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeader(employerToken)),
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
                "/api/statement/" + payPeriodId + "/worksession",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeader(employerToken)),
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
                "/api/statement/" + payPeriodId + "/ewarequest",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeader(employerToken)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void 다른_고용주_접근_시_예외() {
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/statement/" + payPeriodId + "/payperiod",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeader(employerToken2)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}