package com.wageclock.wageclock.domain.worker;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
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


import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class WorkerIntegrationTest {

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

    private String workerToken;

    @AfterEach
    void tearDown() {
        workerRepository.deleteAll();
        employerRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        testRestTemplate.postForEntity("/api/auth/signup",
                new SignupRequest("박사원", "worker@test.com", "password", UserRole.WORKER), Void.class);
        workerToken = testRestTemplate.postForEntity("/api/auth/login",
                        new LoginRequest("worker@test.com", "password", UserRole.WORKER), LoginResponse.class)
                .getBody().token();
    }

    private HttpHeaders authHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    @Test
    void 계좌_등록_성공() {
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/worker/register-account-info",
                HttpMethod.POST,
                new HttpEntity<>(new RegisterAccountInfo("123456789012", "SHINHAN", "박사원"), authHeader(workerToken)),
                Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        Worker worker = workerRepository.findByEmail("worker@test.com").get();
        assertEquals("123456789012", worker.getAccountNumber());
        assertEquals("SHINHAN", worker.getBankCode());
        assertEquals("박사원", worker.getAccountHolder());
    }

    @Test
    void 인증_없이_접근_시_예외() {
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/worker/register-account-info",
                HttpMethod.POST,
                new HttpEntity<>(new RegisterAccountInfo("123456789012", "SHINHAN", "박사원")),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}