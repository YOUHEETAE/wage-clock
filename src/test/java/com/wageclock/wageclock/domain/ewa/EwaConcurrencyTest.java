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
import com.wageclock.wageclock.domain.worksession.WorkSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class EwaConcurrencyTest {

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

    @LocalServerPort
    int port;

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

    private String workerToken;
    private Long sessionId;
    private final RestTemplate restTemplate = new RestTemplate();

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
        String base = "http://localhost:" + port;

        restTemplate.postForEntity(base + "/api/auth/signup",
                new SignupRequest("김사장", "employer@test.com", "password", UserRole.EMPLOYER), Void.class);
        restTemplate.postForEntity(base + "/api/auth/signup",
                new SignupRequest("박사원", "worker@test.com", "password", UserRole.WORKER), Void.class);

        ResponseEntity<LoginResponse> employerResponse = restTemplate.postForEntity(base + "/api/auth/login",
                new LoginRequest("employer@test.com", "password", UserRole.EMPLOYER), LoginResponse.class);
        ResponseEntity<LoginResponse> workerResponse = restTemplate.postForEntity(base + "/api/auth/login",
                new LoginRequest("worker@test.com", "password", UserRole.WORKER), LoginResponse.class);

        String employerToken = employerResponse.getBody().token();
        workerToken = workerResponse.getBody().token();

        Long workerId = workerRepository.findByEmail("worker@test.com").get().getId();

        // 시급 3,600,000 → 1초당 1,000원 적립
        HttpHeaders employerHeaders = new HttpHeaders();
        employerHeaders.set("Authorization", "Bearer " + employerToken);
        HttpEntity<CreateEmploymentRequest> employmentRequest = new HttpEntity<>(
                new CreateEmploymentRequest(workerId, BigDecimal.valueOf(3_600_000)), employerHeaders);
        ResponseEntity<CreateEmploymentResponse> employmentResponse = restTemplate.postForEntity(
                base + "/api/employment", employmentRequest, CreateEmploymentResponse.class);
        Long employmentId = employmentResponse.getBody().employmentId();

        HttpHeaders workerHeaders = new HttpHeaders();
        workerHeaders.set("Authorization", "Bearer " + workerToken);
        HttpEntity<ClockInRequest> clockInRequest = new HttpEntity<>(new ClockInRequest(employmentId), workerHeaders);
        ResponseEntity<ClockInResponse> clockInResponse = restTemplate.postForEntity(
                base + "/api/worksession/clockIn", clockInRequest, ClockInResponse.class);
        sessionId = clockInResponse.getBody().sessionId();

        // 1초 대기 → 약 1,000원 적립, 한도 약 300원
        Thread.sleep(1000);
    }

    @Test
    void 동시_EWA_요청_중_하나만_성공() throws InterruptedException {
        int threadCount = 5;
        String base = "http://localhost:" + port;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + workerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    EwaRequestDto requestDto = new EwaRequestDto(sessionId, BigDecimal.valueOf(300), UUID.randomUUID().toString());
                    HttpEntity<EwaRequestDto> request = new HttpEntity<>(requestDto, headers);
                    ResponseEntity<EwaResponseDto> response = restTemplate.postForEntity(
                            base + "/api/ewaRequest/request", request, EwaResponseDto.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            }));
        }

        latch.countDown();
        for (Future<?> future : futures) {
            try { future.get(); } catch (Exception ignored) {}
        }
        executor.shutdown();

        assertEquals(1, successCount.get());
    }
}
