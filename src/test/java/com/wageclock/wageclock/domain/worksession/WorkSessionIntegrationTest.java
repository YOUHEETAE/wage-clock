package com.wageclock.wageclock.domain.worksession;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employment.EmploymentRequest;
import com.wageclock.wageclock.domain.employment.EmploymentResponse;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class WorkSessionIntegrationTest {

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
    PayPeriodRepository payPeriodRepository;

    private String workerToken;
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
    void setUp() {
        SignupRequest signupEmployerRequest = new SignupRequest("김사장", "employer@test.com", "password", UserRole.EMPLOYER);
        SignupRequest signupWorkerRequest = new SignupRequest("박사원", "worker@test.com", "password", UserRole.WORKER);
        LoginRequest loginEmployerRequest = new LoginRequest("employer@test.com", "password", UserRole.EMPLOYER);
        LoginRequest loginWorkerRequest = new LoginRequest("worker@test.com", "password", UserRole.WORKER);

        testRestTemplate.postForEntity("/api/auth/sign-up", signupEmployerRequest, Void.class);
        testRestTemplate.postForEntity("/api/auth/sign-up", signupWorkerRequest, Void.class);

        ResponseEntity<LoginResponse> employerResponse = testRestTemplate.postForEntity("/api/auth/login", loginEmployerRequest, LoginResponse.class);
        ResponseEntity<LoginResponse> workerResponse = testRestTemplate.postForEntity("/api/auth/login", loginWorkerRequest, LoginResponse.class);

        String employerToken = employerResponse.getBody().token();
        workerToken = workerResponse.getBody().token();

        Long workerId = workerRepository.findByEmail("worker@test.com").get().getId();
        EmploymentRequest employmentRequest = new EmploymentRequest(workerId, BigDecimal.valueOf(10000));
        HttpHeaders employerHeaders = new HttpHeaders();
        employerHeaders.set("Authorization", "Bearer " + employerToken);
        HttpEntity<EmploymentRequest> employmentHttpRequest = new HttpEntity<>(employmentRequest, employerHeaders);
        ResponseEntity<EmploymentResponse> employmentResponse = testRestTemplate.postForEntity("/api/employments", employmentHttpRequest, EmploymentResponse.class);
        employmentId = employmentResponse.getBody().employmentId();
    }

    @Test
    void 정상_clockIn() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + workerToken);
        HttpEntity<ClockInRequest> request = new HttpEntity<>(new ClockInRequest(employmentId), headers);

        ResponseEntity<ClockInResponse> response = testRestTemplate.postForEntity("/api/work-sessions/clock-in", request, ClockInResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().sessionId());
        assertNotNull(response.getBody().clockIn());
    }

    @Test
    void 정상_clockOut() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + workerToken);
        HttpEntity<ClockInRequest> clockInRequest = new HttpEntity<>(new ClockInRequest(employmentId), headers);
        ResponseEntity<ClockInResponse> clockInResponse = testRestTemplate.postForEntity("/api/work-sessions/clock-in", clockInRequest, ClockInResponse.class);
        Long sessionId = clockInResponse.getBody().sessionId();

        HttpEntity<ClockOutRequest> clockOutRequest = new HttpEntity<>(new ClockOutRequest(sessionId), headers);
        ResponseEntity<ClockOutResponse> clockOutResponse = testRestTemplate.postForEntity("/api/work-sessions/clock-out", clockOutRequest, ClockOutResponse.class);

        assertEquals(HttpStatus.OK, clockOutResponse.getStatusCode());
        assertNotNull(clockOutResponse.getBody().clockOut());
        assertNotNull(clockOutResponse.getBody().earnedAmount());
    }

    @Test
    void 정상_pause() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + workerToken);

        ResponseEntity<ClockInResponse> clockInResponse = testRestTemplate.postForEntity(
                "/api/work-sessions/clock-in", new HttpEntity<>(new ClockInRequest(employmentId), headers), ClockInResponse.class);
        Long sessionId = clockInResponse.getBody().sessionId();

        ResponseEntity<Void> pauseResponse = testRestTemplate.postForEntity(
                "/api/work-sessions/pause", new HttpEntity<>(new ClockOutRequest(sessionId), headers), Void.class);

        assertEquals(HttpStatus.OK, pauseResponse.getStatusCode());
        WorkSession workSession = workSessionRepository.findById(sessionId).get();
        assertEquals(WorkSession.WorkSessionStatus.PAUSED, workSession.getStatus());
    }

    @Test
    void 정상_resume() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + workerToken);

        ResponseEntity<ClockInResponse> clockInResponse = testRestTemplate.postForEntity(
                "/api/work-sessions/clock-in", new HttpEntity<>(new ClockInRequest(employmentId), headers), ClockInResponse.class);
        Long sessionId = clockInResponse.getBody().sessionId();

        testRestTemplate.postForEntity(
                "/api/work-sessions/pause", new HttpEntity<>(new ClockOutRequest(sessionId), headers), Void.class);
        ResponseEntity<Void> resumeResponse = testRestTemplate.postForEntity(
                "/api/work-sessions/resume", new HttpEntity<>(new ClockOutRequest(sessionId), headers), Void.class);

        assertEquals(HttpStatus.OK, resumeResponse.getStatusCode());
        WorkSession workSession = workSessionRepository.findById(sessionId).get();
        assertEquals(WorkSession.WorkSessionStatus.WORKING, workSession.getStatus());
    }
}