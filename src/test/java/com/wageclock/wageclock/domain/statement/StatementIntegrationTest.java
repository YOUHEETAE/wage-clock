package com.wageclock.wageclock.domain.statement;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class StatementIntegrationTest extends IntegrationTestBase {

    private String employerToken;
    private String employerToken2;
    private Long employmentId;
    private Long payPeriodId;

    @AfterEach
    void tearDown() {
        cleanCommon();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        signUp("김사장", "employer@test.com", UserRole.EMPLOYER);
        signUp("이사장", "employer2@test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@test.com", UserRole.WORKER);
        employerToken = login("employer@test.com");
        employerToken2 = login("employer2@test.com");
        String workerToken = login("worker@test.com");

        employmentId = createEmployment("worker@test.com", BigDecimal.valueOf(10000), "테스트 사업장", employerToken);
        Long sessionId = clockIn(employmentId, workerToken);
        Thread.sleep(1000);
        clockOut(sessionId, workerToken);

        testRestTemplate.postForEntity(
                "/api/pay-periods/" + employmentId + "/close",
                new HttpEntity<>(null, authHeaders(employerToken)), Void.class);

        payPeriodId = payPeriodRepository
                .findByEmploymentIdAndStatus(employmentId, PayPeriod.PayPeriodStatus.CLOSED)
                .get().getId();
    }

    @Test
    void 정산명세서_조회() {
        ResponseEntity<Map> response = testRestTemplate.exchange(
                "/api/statements/" + payPeriodId + "/pay-period",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().get("periodStart"));
        assertNotNull(response.getBody().get("periodEnd"));
        assertNotNull(response.getBody().get("totalEarnedAmount"));
        assertNotNull(response.getBody().get("totalEwaAmount"));
        assertNotNull(response.getBody().get("actualPayAmount"));
        assertEquals("박사원", response.getBody().get("workerName"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 워크세션_이력_조회() {
        ResponseEntity<List> response = testRestTemplate.exchange(
                "/api/statements/" + payPeriodId + "/work-sessions",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        Map<String, Object> session = (Map<String, Object>) response.getBody().get(0);
        assertNotNull(session.get("clockIn"));
        assertNotNull(session.get("clockOut"));
        assertNotNull(session.get("earnedAmount"));
        assertNotNull(session.get("hourlyWage"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void EWA_이력_조회_빈_리스트() {
        ResponseEntity<List> response = testRestTemplate.exchange(
                "/api/statements/" + payPeriodId + "/ewa-requests",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void 다른_고용주_접근_시_예외() {
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/statements/" + payPeriodId + "/pay-period",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken2)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
