package com.wageclock.wageclock.domain.worksession;

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

import static org.junit.jupiter.api.Assertions.*;

public class WorkSessionIntegrationTest extends IntegrationTestBase {

    private String workerToken;
    private Long employmentId;

    @AfterEach
    void tearDown() {
        cleanCommon();
    }

    @BeforeEach
    void setUp() {
        signUp("김사장", "employer@test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@test.com", UserRole.WORKER);
        String employerToken = login("employer@test.com");
        workerToken = login("worker@test.com");
        Long workplaceId = createWorkplace("테스트 사업장", employerToken);
        employmentId = createEmployment("worker@test.com", BigDecimal.valueOf(10000), workplaceId, employerToken);
    }

    @Test
    void 정상_clockIn() {
        ResponseEntity<ClockInResponse> response = testRestTemplate.postForEntity(
                "/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), authHeaders(workerToken)),
                ClockInResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().sessionId());
        assertNotNull(response.getBody().clockIn());
    }

    @Test
    void 정상_clockOut() {
        Long sessionId = clockIn(employmentId, workerToken);

        ResponseEntity<ClockOutResponse> clockOutResponse = testRestTemplate.postForEntity(
                "/api/work-sessions/clock-out",
                new HttpEntity<>(new ClockOutRequest(sessionId), authHeaders(workerToken)),
                ClockOutResponse.class);

        assertEquals(HttpStatus.OK, clockOutResponse.getStatusCode());
        assertNotNull(clockOutResponse.getBody().clockOut());
        assertNotNull(clockOutResponse.getBody().earnedAmount());
    }

    @Test
    void 정상_pause() {
        Long sessionId = clockIn(employmentId, workerToken);

        ResponseEntity<PauseResponse> pauseResponse = testRestTemplate.postForEntity(
                "/api/work-sessions/pause",
                new HttpEntity<>(new ClockOutRequest(sessionId), authHeaders(workerToken)),
                PauseResponse.class);

        assertEquals(HttpStatus.OK, pauseResponse.getStatusCode());
        assertNotNull(pauseResponse.getBody().earnedAmount());
        WorkSession workSession = workSessionRepository.findById(sessionId).get();
        assertEquals(WorkSession.WorkSessionStatus.PAUSED, workSession.getStatus());
    }

    @Test
    void 현재_WORKING_세션_조회() {
        Long sessionId = clockIn(employmentId, workerToken);

        ResponseEntity<CurrentSessionResponse> response = testRestTemplate.exchange(
                "/api/work-sessions/current?employmentId=" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(workerToken)),
                CurrentSessionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(sessionId, response.getBody().sessionId());
        assertEquals(WorkSession.WorkSessionStatus.WORKING, response.getBody().status());
        assertNotNull(response.getBody().hourlyWage());
        assertNotNull(response.getBody().lastResumeAt());
    }

    @Test
    void 현재_PAUSED_세션_조회() {
        Long sessionId = clockIn(employmentId, workerToken);
        testRestTemplate.postForEntity("/api/work-sessions/pause",
                new HttpEntity<>(new ClockOutRequest(sessionId), authHeaders(workerToken)), PauseResponse.class);

        ResponseEntity<CurrentSessionResponse> response = testRestTemplate.exchange(
                "/api/work-sessions/current?employmentId=" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(workerToken)),
                CurrentSessionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(sessionId, response.getBody().sessionId());
        assertEquals(WorkSession.WorkSessionStatus.PAUSED, response.getBody().status());
        assertNotNull(response.getBody().earnedAmount());
    }

    @Test
    void 세션_없을_때_204_반환() {
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/work-sessions/current?employmentId=" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(workerToken)),
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void 다른_워커_접근_시_401() {
        signUp("다른사원", "other@test.com", UserRole.WORKER);
        String otherToken = login("other@test.com");

        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/work-sessions/current?employmentId=" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(otherToken)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void 정상_resume() {
        Long sessionId = clockIn(employmentId, workerToken);
        testRestTemplate.postForEntity(
                "/api/work-sessions/pause",
                new HttpEntity<>(new ClockOutRequest(sessionId), authHeaders(workerToken)),
                PauseResponse.class);

        ResponseEntity<ResumeResponse> resumeResponse = testRestTemplate.postForEntity(
                "/api/work-sessions/resume",
                new HttpEntity<>(new ClockOutRequest(sessionId), authHeaders(workerToken)),
                ResumeResponse.class);

        assertEquals(HttpStatus.OK, resumeResponse.getStatusCode());
        assertNotNull(resumeResponse.getBody().lastResumeAt());
        WorkSession workSession = workSessionRepository.findById(sessionId).get();
        assertEquals(WorkSession.WorkSessionStatus.WORKING, workSession.getStatus());
    }
}
