package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.worksession.ClockInRequest;
import com.wageclock.wageclock.domain.worksession.ClockInResponse;
import com.wageclock.wageclock.domain.worksession.ClockOutRequest;
import com.wageclock.wageclock.domain.worksession.WorkSession;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PayPeriodIntegrationTest extends IntegrationTestBase {

    private String workerToken;
    private String employerToken;
    private Long employmentId;
    private Long sessionId;
    private Long workplaceId;

    @AfterEach
    void tearDown() {
        cleanCommon();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        signUp("김사장", "employer@test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@test.com", UserRole.WORKER);
        employerToken = login("employer@test.com");
        workerToken = login("worker@test.com");
        workplaceId = createWorkplace("테스트 사업장", employerToken);
        // 시급 3,600,000 → 1초당 1,000원 적립
        employmentId = createEmployment("worker@test.com", BigDecimal.valueOf(3_600_000), workplaceId, employerToken);
        sessionId = clockIn(employmentId, workerToken);
        // 2초 대기 → 약 2,000원 적립 → 한도 약 600원
        Thread.sleep(2000);
        clockOut(sessionId, workerToken);
    }

    @Test
    void 정상_summary_조회() {
        ResponseEntity<PayPeriodSummaryResponse> response = testRestTemplate.exchange(
                "/api/pay-periods/" + employmentId + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(workerToken)),
                PayPeriodSummaryResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().totalEarnedAmount().compareTo(BigDecimal.valueOf(2000)));
        assertEquals(0, response.getBody().totalEwaAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    void WORKING_세션_있을_때_currentEarned_반영() throws InterruptedException {
        testRestTemplate.postForEntity("/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), authHeaders(workerToken)), ClockInResponse.class);
        Thread.sleep(1000);

        ResponseEntity<PayPeriodSummaryResponse> response = testRestTemplate.exchange(
                "/api/pay-periods/" + employmentId + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(workerToken)),
                PayPeriodSummaryResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().totalEarnedAmount().compareTo(BigDecimal.valueOf(2000)) > 0);
    }

    @Test
    void 다른_워커_접근_시_예외() {
        signUp("다른워커", "other@test.com", UserRole.WORKER);
        String otherToken = login("other@test.com");

        ResponseEntity<PayPeriodSummaryResponse> response = testRestTemplate.exchange(
                "/api/pay-periods/" + employmentId + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(otherToken)),
                PayPeriodSummaryResponse.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void summary_activeSessionStatus_WORKING_반환() {
        clockIn(employmentId, workerToken);

        ResponseEntity<PayPeriodSummaryResponse> response = testRestTemplate.exchange(
                "/api/pay-periods/" + employmentId + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(workerToken)),
                PayPeriodSummaryResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(WorkSession.WorkSessionStatus.WORKING, response.getBody().activeSessionStatus());
    }

    @Test
    void summary_activeSessionStatus_세션_없을때_null() {
        ResponseEntity<PayPeriodSummaryResponse> response = testRestTemplate.exchange(
                "/api/pay-periods/" + employmentId + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(workerToken)),
                PayPeriodSummaryResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().activeSessionStatus());
    }

    @Test
    void summaries_정상_조회() {
        ResponseEntity<List<PayPeriodSummaryResponse>> response = testRestTemplate.exchange(
                "/api/pay-periods/summaries?workplaceId=" + workplaceId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(employmentId, response.getBody().get(0).employmentId());
        assertEquals(0, response.getBody().get(0).totalEarnedAmount().compareTo(BigDecimal.valueOf(2000)));
    }

    @Test
    void summaries_WORKING_세션_실시간_반영() throws InterruptedException {
        clockIn(employmentId, workerToken);
        Thread.sleep(1000);

        ResponseEntity<List<PayPeriodSummaryResponse>> response = testRestTemplate.exchange(
                "/api/pay-periods/summaries?workplaceId=" + workplaceId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().get(0).totalEarnedAmount().compareTo(BigDecimal.valueOf(2000)) > 0);
        assertEquals(WorkSession.WorkSessionStatus.WORKING, response.getBody().get(0).activeSessionStatus());
    }

    @Test
    void summary_activeSessionStatus_PAUSED_반환() {
        Long newSessionId = clockIn(employmentId, workerToken);
        testRestTemplate.postForEntity("/api/work-sessions/pause",
                new HttpEntity<>(new ClockOutRequest(newSessionId), authHeaders(workerToken)), Void.class);

        ResponseEntity<PayPeriodSummaryResponse> response = testRestTemplate.exchange(
                "/api/pay-periods/" + employmentId + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(workerToken)),
                PayPeriodSummaryResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(WorkSession.WorkSessionStatus.PAUSED, response.getBody().activeSessionStatus());
    }

    @Test
    void summaries_세션_없을때_activeSessionStatus_null() {
        ResponseEntity<List<PayPeriodSummaryResponse>> response = testRestTemplate.exchange(
                "/api/pay-periods/summaries?workplaceId=" + workplaceId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().get(0).activeSessionStatus());
    }

    @Test
    void summaries_PAUSED_세션_activeSessionStatus_반환() {
        Long newSessionId = clockIn(employmentId, workerToken);
        testRestTemplate.postForEntity("/api/work-sessions/pause",
                new HttpEntity<>(new ClockOutRequest(newSessionId), authHeaders(workerToken)), Void.class);

        ResponseEntity<List<PayPeriodSummaryResponse>> response = testRestTemplate.exchange(
                "/api/pay-periods/summaries?workplaceId=" + workplaceId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(WorkSession.WorkSessionStatus.PAUSED, response.getBody().get(0).activeSessionStatus());
    }
}
