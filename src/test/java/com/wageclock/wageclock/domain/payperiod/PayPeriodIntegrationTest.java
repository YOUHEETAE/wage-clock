package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestDto;
import com.wageclock.wageclock.domain.ewarequest.EwaResponseDto;
import com.wageclock.wageclock.domain.worksession.ClockInRequest;
import com.wageclock.wageclock.domain.worksession.ClockInResponse;
import com.wageclock.wageclock.domain.worksession.ClockOutRequest;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PayPeriodIntegrationTest extends IntegrationTestBase {

    private String workerToken;
    private String employerToken;
    private Long employmentId;
    private Long sessionId;

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
        Long workplaceId = createWorkplace("테스트 사업장", employerToken);
        // 시급 3,600,000 → 1초당 1,000원 적립
        employmentId = createEmployment("worker@test.com", BigDecimal.valueOf(3_600_000), workplaceId, employerToken);
        sessionId = clockIn(employmentId, workerToken);
        // 2초 대기 → 약 2,000원 적립 → 한도 약 600원
        Thread.sleep(2000);
        clockOut(sessionId, workerToken);
    }

    private Long requestEwa(BigDecimal amount) {
        EwaRequestDto requestDto = new EwaRequestDto(employmentId, amount, UUID.randomUUID().toString());
        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/request",
                new HttpEntity<>(requestDto, authHeaders(workerToken)),
                EwaResponseDto.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().ewaRequestId();
    }

    @Test
    void WORKING_상태_workSession_존재_시_예외() {
        testRestTemplate.postForEntity("/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), authHeaders(workerToken)), Void.class);
        ResponseEntity<ClosePayPeriodResponse> response = testRestTemplate.postForEntity(
                "/api/pay-periods/" + employmentId + "/close",
                new HttpEntity<>(null, authHeaders(employerToken)), ClosePayPeriodResponse.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void PAUSED_상태_workSession_존재_시_예외() {
        testRestTemplate.postForEntity("/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), authHeaders(workerToken)), Void.class);
        testRestTemplate.postForEntity("/api/work-sessions/pause",
                new HttpEntity<>(new ClockOutRequest(sessionId), authHeaders(workerToken)), Void.class);
        ResponseEntity<ClosePayPeriodResponse> response = testRestTemplate.postForEntity(
                "/api/pay-periods/" + employmentId + "/close",
                new HttpEntity<>(null, authHeaders(employerToken)), ClosePayPeriodResponse.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void 다른_고용주_close_요청_시_예외() {
        ResponseEntity<ClosePayPeriodResponse> response = testRestTemplate.postForEntity(
                "/api/pay-periods/3/close",
                new HttpEntity<>(null, authHeaders(employerToken)), ClosePayPeriodResponse.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void 정상_close_검증() {
        ResponseEntity<ClosePayPeriodResponse> response = testRestTemplate.postForEntity(
                "/api/pay-periods/" + employmentId + "/close",
                new HttpEntity<>(null, authHeaders(employerToken)), ClosePayPeriodResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        PayPeriod payPeriod = payPeriodRepository.findAll().get(0);
        assertEquals(PayPeriod.PayPeriodStatus.CLOSED, payPeriod.getStatus());
        assertEquals(0, response.getBody().actualPayAmount().compareTo(BigDecimal.valueOf(2000)));
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
}
