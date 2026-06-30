package com.wageclock.wageclock.domain.ewarequest;

import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRepository;
import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.EmploymentRequest;
import com.wageclock.wageclock.domain.employment.EmploymentResponse;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
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
    }

    @Autowired TestRestTemplate testRestTemplate;
    @Autowired WorkerRepository workerRepository;
    @Autowired EmployerRepository employerRepository;
    @Autowired EmploymentRepository employmentRepository;
    @Autowired WorkSessionRepository workSessionRepository;
    @Autowired EwaRequestRepository ewaRequestRepository;
    @Autowired EwaTransferRepository ewaTransferRepository;
    @Autowired PayPeriodRepository payPeriodRepository;

    private String workerToken;
    private String employerToken;
    private Long employmentId;


    @AfterEach
    void tearDown() {
        ewaTransferRepository.deleteAll();
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
        Long sessionId = clockInResponse.getBody().sessionId();

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
    void 정상_EWA_요청() {
        EwaRequestDto requestDto = new EwaRequestDto(employmentId, BigDecimal.valueOf(100), UUID.randomUUID().toString());
        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/request",
                new HttpEntity<>(requestDto, workerHeaders()),
                EwaResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().ewaRequestId());
        assertEquals(EwaRequest.EwaRequestStatus.PENDING, response.getBody().status());
    }

    @Test
    void 한도_초과_EWA_요청_실패() {
        EwaRequestDto requestDto = new EwaRequestDto(employmentId, BigDecimal.valueOf(10000), UUID.randomUUID().toString());
        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/request",
                new HttpEntity<>(requestDto, workerHeaders()),
                Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void 멱등성_키_중복_요청_실패() {
        String key = UUID.randomUUID().toString();
        EwaRequestDto requestDto = new EwaRequestDto(employmentId, BigDecimal.valueOf(100), key);
        testRestTemplate.postForEntity("/api/ewa-requests/request",
                new HttpEntity<>(requestDto, workerHeaders()), EwaResponseDto.class);

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/request",
                new HttpEntity<>(new EwaRequestDto(employmentId, BigDecimal.valueOf(100), key), workerHeaders()),
                Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void 정상_EWA_승인() {

        Long ewaId = requestEwa(BigDecimal.valueOf(100));

        ResponseEntity<InitiateEwaResponse> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, employerHeaders()),
                InitiateEwaResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, response.getBody().status());
    }

    @Test
    void 정상_EWA_거절() {
        Long ewaId = requestEwa(BigDecimal.valueOf(100));

        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/" + ewaId + "/reject",
                new HttpEntity<>(null, employerHeaders()),
                EwaResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EwaRequest.EwaRequestStatus.REJECTED, response.getBody().status());
    }

    @Test
    void 다른_고용주_승인_시도_실패() {

        Long ewaId = requestEwa(BigDecimal.valueOf(100));

        testRestTemplate.postForEntity("/api/auth/sign-up",
                new SignupRequest("다른사장", "other@test.com", "password", UserRole.EMPLOYER), Void.class);
        String otherToken = testRestTemplate.postForEntity("/api/auth/login",
                new LoginRequest("other@test.com", "password"), LoginResponse.class)
                .getBody().token();

        HttpHeaders otherHeaders = new HttpHeaders();
        otherHeaders.set("Authorization", "Bearer " + otherToken);

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, otherHeaders),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void PENDING_아닌_요청_initiate_실패() {
        Long ewaId = requestEwa(BigDecimal.valueOf(100));

        HttpHeaders headers = employerHeaders();
        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/reject",
                new HttpEntity<>(null, headers), EwaResponseDto.class);

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, headers),
                Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void EWA_APPROVE_검증() {
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, employerHeaders()), InitiateEwaResponse.class);

        EwaRequest.EwaRequestStatus status = ewaRequestRepository.findById(ewaId).get().getStatus();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, status);

        List<EwaTransfer> transfers = ewaTransferRepository.findAll();
        assertEquals(1, transfers.size());
        assertEquals(EwaTransfer.EwaTransferStatus.COMPLETED, transfers.get(0).getStatus());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(transfers.get(0).getAmount()));
    }
    @Test
    void 거절_후_한도_복구_재요청_성공(){
        Long ewaId = requestEwa(BigDecimal.valueOf(500));

        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/reject",
                new HttpEntity<>(null, employerHeaders()), Void.class);

        Long newEwaId = requestEwa(BigDecimal.valueOf(500));
        assertNotNull(newEwaId);
    }
}
