package com.wageclock.wageclock.domain.dashboard;

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

public class DashboardIntegrationTest extends IntegrationTestBase {

    private String workerToken;
    private String employerToken;
    private Long workplaceId;
    private Long employmentId;
    private String workerToken2;

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

        workplaceId = createWorkplace("테스트 사업장", employerToken);
        // 시급 3,600,000 → 1초당 1,000원 적립
        employmentId = createEmployment("worker@test.com", BigDecimal.valueOf(3_600_000), workplaceId, employerToken);
        Long employmentId2 = createEmployment("worker2@test.com", BigDecimal.valueOf(3_600_000), workplaceId, employerToken);

        Long sessionId = clockIn(employmentId, workerToken);
        clockIn(employmentId2, workerToken2);

        // 2초 대기 → 약 2,000원 적립 → 한도 약 600원
        Thread.sleep(2000);
        clockOut(sessionId, workerToken);
    }

    @Test
    void 사업장_대시보드_전체_워커_조회() {
        ResponseEntity<DashboardResponse[]> response = testRestTemplate.exchange(
                "/api/dashboards/" + workplaceId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                DashboardResponse[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().length);
    }

    @Test
    void 퇴근한_워커는_status_null_출근중인_워커는_WORKING() {
        ResponseEntity<DashboardResponse[]> response = testRestTemplate.exchange(
                "/api/dashboards/" + workplaceId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                DashboardResponse[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        DashboardResponse worker1 = java.util.Arrays.stream(response.getBody())
                .filter(r -> r.employmentId().equals(employmentId))
                .findFirst().get();
        assertNull(worker1.status());
        assertTrue(worker1.todayEarnedAmount().compareTo(java.math.BigDecimal.ZERO) > 0);

        DashboardResponse worker2 = java.util.Arrays.stream(response.getBody())
                .filter(r -> !r.employmentId().equals(employmentId))
                .findFirst().get();
        assertEquals(com.wageclock.wageclock.domain.worksession.WorkSession.WorkSessionStatus.WORKING, worker2.status());
    }

    @Test
    void 다른_고용주_대시보드_접근_시_예외() {
        signUp("다른사장", "other@test.com", UserRole.EMPLOYER);
        String otherToken = login("other@test.com");

        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/dashboards/" + workplaceId,
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(otherToken)),
                Void.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
