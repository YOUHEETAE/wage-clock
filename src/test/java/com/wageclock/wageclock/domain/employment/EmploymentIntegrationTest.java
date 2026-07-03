package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EmploymentIntegrationTest extends IntegrationTestBase {

    private String employerToken;
    private String workerToken;

    @AfterEach
    void tearDown() {
        cleanCommon();
    }

    @BeforeEach
    public void setup() {
        signUp("김사장", "employer@test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@test.com", UserRole.WORKER);
        employerToken = login("employer@test.com");
        workerToken = login("worker@test.com");
    }

    @Test
    void 정상_employment_생성() {
        EmploymentRequest body = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), "스타벅스 강남점");

        ResponseEntity<EmploymentResponse> response = testRestTemplate.postForEntity(
                "/api/employments",
                new HttpEntity<>(body, authHeaders(employerToken)),
                EmploymentResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().employmentId());
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(response.getBody().hourlyWage()));
        assertEquals("스타벅스 강남점", response.getBody().employmentName());
    }

    @Test
    void 중복_employment_생성_시_예외() {
        EmploymentRequest body = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), "스타벅스 강남점");
        testRestTemplate.postForEntity("/api/employments", new HttpEntity<>(body, authHeaders(employerToken)), EmploymentResponse.class);

        EmploymentRequest duplicateBody = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(20000), "스타벅스 강남점");
        ResponseEntity<EmploymentResponse> duplicateResponse = testRestTemplate.postForEntity(
                "/api/employments",
                new HttpEntity<>(duplicateBody, authHeaders(employerToken)),
                EmploymentResponse.class);

        assertEquals(HttpStatus.CONFLICT, duplicateResponse.getStatusCode());
    }

    @Test
    void 근로자가_employment_생성_시_예외() {
        EmploymentRequest body = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), "스타벅스 강남점");
        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/employments",
                new HttpEntity<>(body, authHeaders(workerToken)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void 내_고용_목록_조회() {
        testRestTemplate.postForEntity("/api/employments",
                new HttpEntity<>(new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), "스타벅스 강남점"), authHeaders(employerToken)),
                EmploymentResponse.class);

        ResponseEntity<EmploymentResponse[]> response = testRestTemplate.exchange(
                "/api/employments/worker", HttpMethod.GET, new HttpEntity<>(authHeaders(workerToken)), EmploymentResponse[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().length);
        assertEquals("스타벅스 강남점", response.getBody()[0].employmentName());
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(response.getBody()[0].hourlyWage()));
    }

    @Test
    void 고용_없을_때_빈_목록_반환() {
        ResponseEntity<EmploymentResponse[]> response = testRestTemplate.exchange(
                "/api/employments/worker", HttpMethod.GET, new HttpEntity<>(authHeaders(workerToken)), EmploymentResponse[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().length);
    }

    @Test
    void 고용주_고용_목록_조회() {
        testRestTemplate.postForEntity("/api/employments",
                new HttpEntity<>(new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), "스타벅스 강남점"), authHeaders(employerToken)),
                EmploymentResponse.class);

        ResponseEntity<EmploymentResponse[]> response = testRestTemplate.exchange(
                "/api/employments/employer", HttpMethod.GET, new HttpEntity<>(authHeaders(employerToken)), EmploymentResponse[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().length);
        assertEquals("스타벅스 강남점", response.getBody()[0].employmentName());
    }

    @Test
    void 근로자가_고용주_엔드포인트_호출_시_예외() {
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/employments/employer", HttpMethod.GET, new HttpEntity<>(authHeaders(workerToken)), Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void 고용주가_근로자_엔드포인트_호출_시_예외() {
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/employments/worker", HttpMethod.GET, new HttpEntity<>(authHeaders(employerToken)), Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
