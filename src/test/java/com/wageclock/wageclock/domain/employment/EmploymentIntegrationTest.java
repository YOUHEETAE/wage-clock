package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.domain.auth.LoginRequest;
import com.wageclock.wageclock.domain.auth.LoginResponse;
import com.wageclock.wageclock.domain.auth.SignupRequest;
import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class EmploymentIntegrationTest {

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

    private String employerToken;
    private String workerToken;
    private Long workerId;


    @AfterEach
    void tearDown() {
        employmentRepository.deleteAll();
        workerRepository.deleteAll();
        employerRepository.deleteAll();
    }

    @BeforeEach
    public void setup() {
        SignupRequest signupEmployerRequest = new SignupRequest("김사장", "employer@test.com", "password", UserRole.EMPLOYER);
        SignupRequest signupWorkerRequest = new SignupRequest("박사원", "worker@test.com", "password", UserRole.WORKER);
        LoginRequest loginEmployerRequest = new LoginRequest("employer@test.com", "password");
        LoginRequest loginWorkerRequest = new LoginRequest("worker@test.com", "password");
        testRestTemplate.postForEntity("/api/auth/sign-up", signupEmployerRequest, Void.class);
        testRestTemplate.postForEntity("/api/auth/sign-up", signupWorkerRequest, Void.class);
        ResponseEntity<LoginResponse> employerResponse = testRestTemplate.postForEntity("/api/auth/login", loginEmployerRequest, LoginResponse.class);
        ResponseEntity<LoginResponse> workerResponse = testRestTemplate.postForEntity("/api/auth/login", loginWorkerRequest, LoginResponse.class);
        employerToken = employerResponse.getBody().token();
        workerToken = workerResponse.getBody().token();
        workerId = workerRepository.findByEmail("worker@test.com").get().getId();
    }

    @Test
    void 정상_employment_생성(){
        EmploymentRequest body = new EmploymentRequest(workerId, BigDecimal.valueOf(10000));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + employerToken);
        HttpEntity<EmploymentRequest> request = new HttpEntity<>(body, headers);
        ResponseEntity<EmploymentResponse> response = testRestTemplate.postForEntity("/api/employments", request, EmploymentResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    @Test
    void 중복_employment_생성_시_예외(){
        EmploymentRequest body = new EmploymentRequest(workerId, BigDecimal.valueOf(10000));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + employerToken);
        HttpEntity<EmploymentRequest> request = new HttpEntity<>(body, headers);
        ResponseEntity<EmploymentResponse> response = testRestTemplate.postForEntity("/api/employments", request, EmploymentResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        EmploymentRequest duplicateBody = new EmploymentRequest(workerId, BigDecimal.valueOf(20000));
        HttpHeaders duplicateHeaders = new HttpHeaders();
        duplicateHeaders.set("Authorization", "Bearer " + employerToken);
        HttpEntity<EmploymentRequest> duplicateRequest = new HttpEntity<>(duplicateBody, duplicateHeaders);
        ResponseEntity<EmploymentResponse> duplicateResponse = testRestTemplate.postForEntity("/api/employments", duplicateRequest, EmploymentResponse.class);
        assertEquals(HttpStatus.CONFLICT, duplicateResponse.getStatusCode());
    }
}
