package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.EmploymentRequest;
import com.wageclock.wageclock.domain.employment.EmploymentResponse;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestDto;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestRepository;
import com.wageclock.wageclock.domain.ewarequest.EwaResponseDto;
import com.wageclock.wageclock.domain.port.VirtualAccountPort;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class PayPeriodIntegrationTest {
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
    @Autowired PayPeriodRepository payPeriodRepository;
    @MockitoBean
    VirtualAccountPort virtualAccountPort;

    private String workerToken;
    private String employerToken;
    private Long employmentId;
    private Long sessionId;


    @AfterEach
    void tearDown() {
        ewaRequestRepository.deleteAll();
        workSessionRepository.deleteAll();
        payPeriodRepository.deleteAll();
        employmentRepository.deleteAll();
        workerRepository.deleteAll();
        employerRepository.deleteAll();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        testRestTemplate.postForEntity("/api/auth/sign-up",
                new SignupRequest("김사장", "employer@test.com", "password", UserRole.EMPLOYER), Void.class);
        testRestTemplate.postForEntity("/api/auth/sign-up",
                new SignupRequest("박사원", "worker@test.com", "password", UserRole.WORKER), Void.class);

        employerToken = testRestTemplate.postForEntity("/api/auth/login",
                        new LoginRequest("employer@test.com", "password"), LoginResponse.class)
                .getBody().token();
        workerToken = testRestTemplate.postForEntity("/api/auth/login",
                        new LoginRequest("worker@test.com", "password"), LoginResponse.class)
                .getBody().token();

        Long workerId = workerRepository.findByEmail("worker@test.com").get().getId();

        // 시급 3,600,000 → 1초당 1,000원 적립
        HttpHeaders employerHeaders = new HttpHeaders();
        employerHeaders.set("Authorization", "Bearer " + employerToken);
        ResponseEntity<EmploymentResponse> employmentResponse = testRestTemplate.postForEntity(
                "/api/employments",
                new HttpEntity<>(new EmploymentRequest(workerId, BigDecimal.valueOf(3_600_000)), employerHeaders),
                EmploymentResponse.class);
        this.employmentId = employmentResponse.getBody().employmentId();

        HttpHeaders workerHeaders = new HttpHeaders();
        workerHeaders.set("Authorization", "Bearer " + workerToken);

        ResponseEntity<ClockInResponse> clockInResponse = testRestTemplate.postForEntity(
                "/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders),
                ClockInResponse.class);
        this.sessionId = clockInResponse.getBody().sessionId();

        // 2초 대기 → 약 2,000원 적립 → 한도 약 600원
        Thread.sleep(2000);
        testRestTemplate.postForEntity("/api/work-sessions/clock-out",
                new HttpEntity<>(new ClockOutRequest(sessionId), workerHeaders), Void.class);
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
                "/api/ewa-requests/request",
                new HttpEntity<>(requestDto, workerHeaders()),
                EwaResponseDto.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().ewaRequestId();
    }
    @Test
    void WORKING_상태_workSession_존재_시_예외(){
        testRestTemplate.postForEntity("/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders()), Void.class);
        ResponseEntity<ClosePayPeriodResponse> response = testRestTemplate.postForEntity(
                "/api/pay-periods/" + employmentId + "/close",
                new HttpEntity<>(null , employerHeaders()), ClosePayPeriodResponse.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
    @Test
    void PAUSED_상태_workSession_존재_시_예외(){
        testRestTemplate.postForEntity("/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders()), Void.class);
        testRestTemplate.postForEntity("/api/work-sessions/pause",
                new HttpEntity<>(new ClockOutRequest(sessionId), workerHeaders()), Void.class);
        ResponseEntity<ClosePayPeriodResponse> response = testRestTemplate.postForEntity(
                "/api/pay-periods/" + employmentId + "/close",
                new HttpEntity<>(null , employerHeaders()), ClosePayPeriodResponse.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
    @Test
    void 다른_고용주_close_요청_시_예외(){
        Long otherEmploymentId = 3L;
        ResponseEntity<ClosePayPeriodResponse> response = testRestTemplate.postForEntity(
                "/api/pay-periods/" + otherEmploymentId + "/close",
                new HttpEntity<>(null , employerHeaders()), ClosePayPeriodResponse.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
    @Test
    void 정상_close_검증(){
        ResponseEntity<ClosePayPeriodResponse> response = testRestTemplate.postForEntity(
                "/api/pay-periods/" + employmentId + "/close",
                new HttpEntity<>(null , employerHeaders()), ClosePayPeriodResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        PayPeriod payPeriod = payPeriodRepository.findAll().get(0);
        assertEquals(PayPeriod.PayPeriodStatus.CLOSED, payPeriod.getStatus());
        assertEquals(0, response.getBody().actualPayAmount().compareTo(BigDecimal.valueOf(2000)));
    }

    @Test
    void 정상_summary_조회() {
        ResponseEntity<PayPeriodSummaryResponse> response = testRestTemplate.exchange(
                "/api/pay-periods/" + employmentId + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(null, workerHeaders()),
                PayPeriodSummaryResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().totalEarnedAmount().compareTo(BigDecimal.valueOf(2000)));
        assertEquals(0, response.getBody().totalEwaAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    void WORKING_세션_있을_때_currentEarned_반영() throws InterruptedException {
        testRestTemplate.postForEntity("/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders()), ClockInResponse.class);
        Thread.sleep(1000);

        ResponseEntity<PayPeriodSummaryResponse> response = testRestTemplate.exchange(
                "/api/pay-periods/" + employmentId + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(null, workerHeaders()),
                PayPeriodSummaryResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().totalEarnedAmount().compareTo(BigDecimal.valueOf(2000)) > 0);
    }

    @Test
    void 다른_워커_접근_시_예외() {
        testRestTemplate.postForEntity("/api/auth/sign-up",
                new SignupRequest("다른워커", "other@test.com", "password", UserRole.WORKER), Void.class);
        String otherToken = testRestTemplate.postForEntity("/api/auth/login",
                        new LoginRequest("other@test.com", "password"), LoginResponse.class)
                .getBody().token();
        HttpHeaders otherHeaders = new HttpHeaders();
        otherHeaders.set("Authorization", "Bearer " + otherToken);

        ResponseEntity<PayPeriodSummaryResponse> response = testRestTemplate.exchange(
                "/api/pay-periods/" + employmentId + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(null, otherHeaders),
                PayPeriodSummaryResponse.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
