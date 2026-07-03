package com.wageclock.wageclock.domain.history;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HistoryIntegrationTest extends IntegrationTestBase {

    private String workerToken;
    private String employerToken;
    private String workerToken2;
    private Long employmentId;

    @AfterEach
    void tearDown() {
        cleanCommon();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        signUp("김사장", "employer@test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@test.com", UserRole.WORKER);
        signUp("유사원", "worker2@test.com", UserRole.WORKER);
        employerToken = login("employer@test.com");
        workerToken = login("worker@test.com");
        workerToken2 = login("worker2@test.com");
        employmentId = createEmployment("worker@test.com", BigDecimal.valueOf(10000), "테스트 사업장", employerToken);
        Long sessionId = clockIn(employmentId, workerToken);
        Thread.sleep(1000);
        clockOut(sessionId, workerToken);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 워커_히스토리_조회() {
        ResponseEntity<Map> response = testRestTemplate.exchange(
                "/api/histories/" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(workerToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(employmentId, ((Number) response.getBody().get("employmentId")).longValue());
        List<Map<String, Object>> events = (List<Map<String, Object>>) response.getBody().get("events");
        assertEquals(3, events.size());
        assertEquals("PAY_PERIOD_START", events.get(0).get("eventType"));
        assertEquals("WORK_SESSION_START", events.get(1).get("eventType"));
        assertEquals("WORK_SESSION_END", events.get(2).get("eventType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 고용주_히스토리_조회() {
        ResponseEntity<Map> response = testRestTemplate.exchange(
                "/api/histories/" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> events = (List<Map<String, Object>>) response.getBody().get("events");
        assertFalse(events.isEmpty());
    }

    @Test
    void 다른_워커_접근_시_예외() {
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/histories/" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(workerToken2)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void size_1로_첫_페이지_조회_hasNext_true_nextCursor_있음() {
        String url = UriComponentsBuilder.fromPath("/api/histories/" + employmentId)
                .queryParam("size", 1)
                .toUriString();
        ResponseEntity<Map> response = testRestTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(null, authHeaders(workerToken)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("hasNext"));
        assertNotNull(response.getBody().get("nextCursor"));
        List<Map<String, Object>> events = (List<Map<String, Object>>) response.getBody().get("events");
        assertEquals(1, events.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 커서_기반_다음_페이지_조회() {
        String firstUrl = UriComponentsBuilder.fromPath("/api/histories/" + employmentId)
                .queryParam("size", 1)
                .toUriString();
        ResponseEntity<Map> firstResponse = testRestTemplate.exchange(
                firstUrl, HttpMethod.GET, new HttpEntity<>(null, authHeaders(workerToken)), Map.class);
        String nextCursor = (String) firstResponse.getBody().get("nextCursor");

        String secondUrl = UriComponentsBuilder.fromPath("/api/histories/" + employmentId)
                .queryParam("size", 1)
                .queryParam("after", nextCursor)
                .toUriString();
        ResponseEntity<Map> secondResponse = testRestTemplate.exchange(
                secondUrl, HttpMethod.GET, new HttpEntity<>(null, authHeaders(workerToken)), Map.class);

        assertEquals(HttpStatus.OK, secondResponse.getStatusCode());
        List<Map<String, Object>> events = (List<Map<String, Object>>) secondResponse.getBody().get("events");
        assertFalse(events.isEmpty());
        assertNotEquals("PAY_PERIOD_START", events.get(0).get("eventType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 전체_사이즈_이상이면_hasNext_false_nextCursor_null() {
        ResponseEntity<Map> response = testRestTemplate.exchange(
                "/api/histories/" + employmentId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(workerToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("hasNext"));
        assertNull(response.getBody().get("nextCursor"));
    }
}
