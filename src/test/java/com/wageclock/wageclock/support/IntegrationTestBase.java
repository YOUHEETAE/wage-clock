package com.wageclock.wageclock.support;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.employment.EmploymentRequest;
import com.wageclock.wageclock.domain.employment.EmploymentResponse;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.domain.worksession.ClockInRequest;
import com.wageclock.wageclock.domain.worksession.ClockInResponse;
import com.wageclock.wageclock.domain.worksession.ClockOutRequest;
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean protected VirtualAccountPort virtualAccountPort;
    @MockitoBean protected WageTransferPort wageTransferPort;

    @Autowired protected TestRestTemplate testRestTemplate;
    @Autowired protected WorkerRepository workerRepository;
    @Autowired protected EmployerRepository employerRepository;
    @Autowired protected EmploymentRepository employmentRepository;
    @Autowired protected WorkSessionRepository workSessionRepository;
    @Autowired protected PayPeriodRepository payPeriodRepository;
    @Autowired protected EwaRequestRepository ewaRequestRepository;

    protected void signUp(String name, String email, UserRole role) {
        testRestTemplate.postForEntity("/api/auth/sign-up",
                new SignupRequest(name, email, "password", role), Void.class);
    }

    protected String login(String email) {
        return testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest(email, "password"), LoginResponse.class)
                .getBody().token();
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    protected Long createEmployment(String workerEmail, BigDecimal hourlyWage, String name, String employerToken) {
        return testRestTemplate.postForEntity("/api/employments",
                new HttpEntity<>(new EmploymentRequest(workerEmail, hourlyWage, name), authHeaders(employerToken)),
                EmploymentResponse.class).getBody().employmentId();
    }

    protected Long clockIn(Long employmentId, String workerToken) {
        return testRestTemplate.postForEntity("/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), authHeaders(workerToken)),
                ClockInResponse.class).getBody().sessionId();
    }

    protected void clockOut(Long sessionId, String workerToken) {
        testRestTemplate.postForEntity("/api/work-sessions/clock-out",
                new HttpEntity<>(new ClockOutRequest(sessionId), authHeaders(workerToken)), Void.class);
    }

    protected void cleanCommon() {
        ewaRequestRepository.deleteAll();
        workSessionRepository.deleteAll();
        payPeriodRepository.deleteAll();
        employmentRepository.deleteAll();
        workerRepository.deleteAll();
        employerRepository.deleteAll();
    }
}
