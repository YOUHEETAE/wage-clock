package com.wageclock.wageclock.domain.auth;

import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
public class AuthIntegrationTest {

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
    EmployerRepository employerRepository;
    @Autowired
    WorkerRepository workerRepository;

    private SignupRequest signupRequest;
    private LoginRequest loginRequest;
    private String token;

    @AfterEach
    void tearDown() {
        workerRepository.deleteAll();
        employerRepository.deleteAll();
    }

    @BeforeEach
    void setUp(){
        signupRequest = new SignupRequest("홍길동", "test@test.com", "password", UserRole.EMPLOYER);
        loginRequest = new LoginRequest("test@test.com", "password", UserRole.EMPLOYER);
        testRestTemplate.postForEntity("/api/auth/sign-up", signupRequest, void.class);
        ResponseEntity<LoginResponse> response = testRestTemplate.postForEntity("/api/auth/login",loginRequest, LoginResponse.class);
        Assertions.assertNotNull(response.getBody());
        token = response.getBody().token();
    }

    @Test
    void 정상_회원가입(){
        signupRequest = new SignupRequest("아무개", "other@test.com", "password1", UserRole.WORKER);
        ResponseEntity<Void> response = testRestTemplate.postForEntity("/api/auth/sign-up", signupRequest, void.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void 중복_회원가입_시_예외(){
        ResponseEntity<Void> response = testRestTemplate.postForEntity("/api/auth/sign-up", signupRequest, Void.class);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void 정상_로그인(){
        ResponseEntity<LoginResponse> response = testRestTemplate.postForEntity("/api/auth/login", loginRequest, LoginResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().token());
    }
    @Test
    void 이메일_없을_시_예외(){
        loginRequest = new LoginRequest("wrongEmail",  "password", UserRole.EMPLOYER);
        ResponseEntity<LoginResponse> response = testRestTemplate.postForEntity("/api/auth/login", loginRequest, LoginResponse.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
    @Test
    void 비밀번호_불일치_시_예외(){
        loginRequest = new LoginRequest("test@test.com",  "wrongPassword", UserRole.EMPLOYER);
        ResponseEntity<LoginResponse> response = testRestTemplate.postForEntity("/api/auth/login", loginRequest, LoginResponse.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void 정상_로그아웃(){
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<?> request = new HttpEntity<>(headers);
        ResponseEntity<Void> response = testRestTemplate.exchange("/api/auth/logout", HttpMethod.POST, request, Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
