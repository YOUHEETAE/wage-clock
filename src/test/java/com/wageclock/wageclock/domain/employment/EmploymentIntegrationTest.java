package com.wageclock.wageclock.domain.employment;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
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

    private Long workplaceId;

    @BeforeEach
    public void setup() {
        signUp("김사장", "employer@test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@test.com", UserRole.WORKER);
        employerToken = login("employer@test.com");
        workerToken = login("worker@test.com");
        workplaceId = createWorkplace("스타벅스 강남점", employerToken);
    }

    @Test
    void 정상_employment_생성() {
        EmploymentRequest body = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), workplaceId);

        ResponseEntity<EmploymentResponse> response = testRestTemplate.postForEntity(
                "/api/employments",
                new HttpEntity<>(body, authHeaders(employerToken)),
                EmploymentResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().employmentId());
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(response.getBody().hourlyWage()));
        assertEquals("스타벅스 강남점", response.getBody().workplaceName());
        assertEquals(workplaceId, response.getBody().workplaceId());
    }

    @Test
    void 중복_employment_생성_시_예외() {
        testRestTemplate.postForEntity("/api/employments",
                new HttpEntity<>(new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), workplaceId), authHeaders(employerToken)),
                EmploymentResponse.class);

        ResponseEntity<EmploymentResponse> duplicateResponse = testRestTemplate.postForEntity(
                "/api/employments",
                new HttpEntity<>(new EmploymentRequest("worker@test.com", BigDecimal.valueOf(20000), workplaceId), authHeaders(employerToken)),
                EmploymentResponse.class);

        assertEquals(HttpStatus.CONFLICT, duplicateResponse.getStatusCode());
    }

    @Test
    void 근로자가_employment_생성_시_예외() {
        EmploymentRequest body = new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), workplaceId);
        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/employments",
                new HttpEntity<>(body, authHeaders(workerToken)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void 타인_workplace로_employment_생성_시_예외() {
        signUp("다른사장", "other@test.com", UserRole.EMPLOYER);
        String otherToken = login("other@test.com");

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/employments",
                new HttpEntity<>(new EmploymentRequest("worker@test.com", BigDecimal.valueOf(10000), workplaceId), authHeaders(otherToken)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
