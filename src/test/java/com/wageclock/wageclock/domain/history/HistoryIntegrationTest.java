package com.wageclock.wageclock.domain.history;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.CreateEmploymentRequest;
import com.wageclock.wageclock.domain.employment.CreateEmploymentResponse;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
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
public class HistoryIntegrationTest {

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
    @Autowired PayPeriodRepository payPeriodRepository;
    @MockitoBean
    VirtualAccountPort virtualAccountPort;

    private String workerToken;
    private String employerToken;
    private String workerToken2;
    private Long employmentId;

    @AfterEach
    void tearDown() {
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
    }

    @Test
    @SuppressWarnings("unchecked")
    void 워커_히스토리_조회() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + workerToken);
        ResponseEntity<Map> response = testRestTemplate.exchange(
                "/api/history/" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(employmentId, ((Number) response.getBody().get("employmentId")).longValue());
        List<Map<String, Object>> events = (List<Map<String, Object>>) response.getBody().get("events");
        // PAY_PERIOD_START, WORK_SESSION_START, WORK_SESSION_END
        assertEquals(3, events.size());
        assertEquals("PAY_PERIOD_START", events.get(0).get("eventType"));
        assertEquals("WORK_SESSION_START", events.get(1).get("eventType"));
        assertEquals("WORK_SESSION_END", events.get(2).get("eventType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 고용주_히스토리_조회() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + employerToken);
        ResponseEntity<Map> response = testRestTemplate.exchange(
                "/api/history/" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> events = (List<Map<String, Object>>) response.getBody().get("events");
        assertFalse(events.isEmpty());
    }

    @Test
    void 다른_워커_접근_시_예외() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + workerToken2);
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/history/" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}