package com.wageclock.wageclock.domain.outbox;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestDto;
import com.wageclock.wageclock.domain.ewarequest.EwaResponseDto;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

public class OutBoxIntegrationTest extends IntegrationTestBase {

    @Autowired OutBoxScheduler outBoxScheduler;

    String employerToken;
    String workerToken;
    Long employmentId;

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
        // 시급 3,600,000 → 1초당 1,000원 적립
        employmentId = createEmployment("worker@test.com", BigDecimal.valueOf(3_600_000), "테스트 사업장", employerToken);
        Long sessionId = clockIn(employmentId, workerToken);
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
}
