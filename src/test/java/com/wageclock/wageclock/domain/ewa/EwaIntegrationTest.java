package com.wageclock.wageclock.domain.ewa;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.CreateEmploymentRequest;
import com.wageclock.wageclock.domain.employment.CreateEmploymentResponse;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class EwaIntegrationTest {

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
        registry.add("JWT_SECRET", () -> "wageclock-secret-key-must-be-at-least-256-bits-long");
    }

    @Autowired TestRestTemplate testRestTemplate;
    @Autowired WorkerRepository workerRepository;
    @Autowired EmployerRepository employerRepository;
    @Autowired EmploymentRepository employmentRepository;
    @Autowired WorkSessionRepository workSessionRepository;
    @Autowired EwaRequestRepository ewaRequestRepository;

    private String workerToken;
    private String employerToken;
    private Long sessionId;

    @AfterEach
    void tearDown() {
        ewaRequestRepository.deleteAll();
        workSessionRepository.deleteAll();
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

        employerToken = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("employer@test.com", "password", UserRole.EMPLOYER), LoginResponse.class)
                .getBody().token();
        workerToken = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("worker@test.com", "password", UserRole.WORKER), LoginResponse.class)
                .getBody().token();

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
    void 정상_EWA_요청() {
        EwaRequestDto requestDto = new EwaRequestDto(sessionId, BigDecimal.valueOf(100), UUID.randomUUID().toString());
        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewaRequest/request",
                new HttpEntity<>(requestDto, workerHeaders()),
                EwaResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().ewaRequestId());
        assertEquals(EwaRequest.EwaRequestStatus.PENDING, response.getBody().status());
    }

    @Test
    void 한도_초과_EWA_요청_실패() {
        EwaRequestDto requestDto = new EwaRequestDto(sessionId, BigDecimal.valueOf(10000), UUID.randomUUID().toString());
        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewaRequest/request",
                new HttpEntity<>(requestDto, workerHeaders()),
                Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void 멱등성_키_중복_요청_실패() {
        String key = UUID.randomUUID().toString();
        EwaRequestDto requestDto = new EwaRequestDto(sessionId, BigDecimal.valueOf(100), key);
        testRestTemplate.postForEntity("/api/ewaRequest/request",
                new HttpEntity<>(requestDto, workerHeaders()), EwaResponseDto.class);

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewaRequest/request",
                new HttpEntity<>(new EwaRequestDto(sessionId, BigDecimal.valueOf(100), key), workerHeaders()),
                Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void 정상_EWA_승인() {
        Long ewaId = requestEwa(BigDecimal.valueOf(100));

        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewaRequest/" + ewaId + "/approve",
                new HttpEntity<>(null, employerHeaders()),
                EwaResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, response.getBody().status());
    }

    @Test
    void 정상_EWA_거절() {
        Long ewaId = requestEwa(BigDecimal.valueOf(100));

        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewaRequest/" + ewaId + "/reject",
                new HttpEntity<>(null, employerHeaders()),
                EwaResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EwaRequest.EwaRequestStatus.REJECTED, response.getBody().status());
    }

    @Test
    void 다른_고용주_승인_시도_실패() {
        Long ewaId = requestEwa(BigDecimal.valueOf(100));

        testRestTemplate.postForEntity("/api/auth/signup",
                new SignupRequest("다른사장", "other@test.com", "password", UserRole.EMPLOYER), Void.class);
        String otherToken = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("other@test.com", "password", UserRole.EMPLOYER), LoginResponse.class)
                .getBody().token();

        HttpHeaders otherHeaders = new HttpHeaders();
        otherHeaders.set("Authorization", "Bearer " + otherToken);

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewaRequest/" + ewaId + "/approve",
                new HttpEntity<>(null, otherHeaders),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void PENDING_아닌_요청_승인_실패() {
        Long ewaId = requestEwa(BigDecimal.valueOf(100));

        // 먼저 승인
        testRestTemplate.postForEntity("/api/ewaRequest/" + ewaId + "/approve",
                new HttpEntity<>(null, employerHeaders()), EwaResponseDto.class);

        // 이미 APPROVED 상태에서 다시 승인 시도
        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewaRequest/" + ewaId + "/approve",
                new HttpEntity<>(null, employerHeaders()),
                Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
