package com.wageclock.wageclock.domain.workplace;

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

public class WorkplaceIntegrationTest extends IntegrationTestBase {

    private String employerToken;
    private String workerToken;

    @AfterEach
    void tearDown() {
        cleanCommon();
    }

    @BeforeEach
    void setUp() {
        signUp("김사장", "employer@test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@test.com", UserRole.WORKER);
        employerToken = login("employer@test.com");
        workerToken = login("worker@test.com");
    }

    @Test
    void 정상_사업장_생성() {
        ResponseEntity<WorkplaceResponse> response = testRestTemplate.postForEntity(
                "/api/workplaces",
                new HttpEntity<>(new WorkplaceRequest("스타벅스 강남점", "서울 강남구"), authHeaders(employerToken)),
                WorkplaceResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().workplaceId());
        assertEquals("스타벅스 강남점", response.getBody().name());
        assertEquals("서울 강남구", response.getBody().address());
    }

    @Test
    void 주소_없는_사업장_생성() {
        ResponseEntity<WorkplaceResponse> response = testRestTemplate.postForEntity(
                "/api/workplaces",
                new HttpEntity<>(new WorkplaceRequest("스타벅스 강남점", null), authHeaders(employerToken)),
                WorkplaceResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().address());
    }

    @Test
    void 워커가_사업장_생성_시_예외() {
        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/workplaces",
                new HttpEntity<>(new WorkplaceRequest("스타벅스 강남점", null), authHeaders(workerToken)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void 고용주_사업장_목록_조회() {
        createWorkplace("스타벅스 강남점", employerToken);
        createWorkplace("맥도날드 서초점", employerToken);

        ResponseEntity<WorkplaceResponse[]> response = testRestTemplate.exchange(
                "/api/workplaces",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(employerToken)),
                WorkplaceResponse[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().length);
    }

    @Test
    void 다른_고용주_사업장은_조회되지_않음() {
        createWorkplace("스타벅스 강남점", employerToken);
        signUp("다른사장", "other@test.com", UserRole.EMPLOYER);
        String otherToken = login("other@test.com");
        createWorkplace("맥도날드 서초점", otherToken);

        ResponseEntity<WorkplaceResponse[]> response = testRestTemplate.exchange(
                "/api/workplaces",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(employerToken)),
                WorkplaceResponse[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().length);
        assertEquals("스타벅스 강남점", response.getBody()[0].name());
    }

    @Test
    void 워커_본인_사업장_목록_조회() {
        signUp("다른사장", "other@test.com", UserRole.EMPLOYER);
        String otherToken = login("other@test.com");
        Long workplaceId1 = createWorkplace("스타벅스 강남점", employerToken);
        Long workplaceId2 = createWorkplace("맥도날드 서초점", otherToken);
        createEmployment("worker@test.com", BigDecimal.valueOf(10000), workplaceId1, employerToken);
        createEmployment("worker@test.com", BigDecimal.valueOf(12000), workplaceId2, otherToken);

        ResponseEntity<WorkerWorkplaceResponse[]> response = testRestTemplate.exchange(
                "/api/workplaces/worker",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(workerToken)),
                WorkerWorkplaceResponse[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().length);
        assertNotNull(response.getBody()[0].employmentId());
        assertNotNull(response.getBody()[0].workplaceId());
    }

    @Test
    void 고용주가_워커_엔드포인트_호출_시_예외() {
        ResponseEntity<Void> response = testRestTemplate.exchange(
                "/api/workplaces/worker",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(employerToken)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
