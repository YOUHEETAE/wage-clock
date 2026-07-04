package com.wageclock.wageclock.domain.ewarequest;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.worksession.WorkSessionService;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EwaConcurrencyTest extends IntegrationTestBase {

    @LocalServerPort int port;
    @Autowired WorkSessionService workSessionService;

    private String workerToken;
    private Long employmentId;
    private final RestTemplate restTemplate = new RestTemplate();

    @AfterEach
    void tearDown() {
        cleanCommon();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        signUp("김사장", "employer@test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@test.com", UserRole.WORKER);
        String employerToken = login("employer@test.com");
        workerToken = login("worker@test.com");
        Long workplaceId = createWorkplace("테스트 사업장", employerToken);
        // 시급 3,600,000 → 1초당 1,000원 적립
        employmentId = createEmployment("worker@test.com", BigDecimal.valueOf(3_600_000), workplaceId, employerToken);
        Long sessionId = clockIn(employmentId, workerToken);
        // 1초 대기 → 약 1,000원 적립, 한도 약 300원
        Thread.sleep(1000);
        Long workerId = workerRepository.findByEmail("worker@test.com").get().getId();
        workSessionService.pause(sessionId, workerId);
    }

    @Test
    void 동시_EWA_요청_중_하나만_성공() throws InterruptedException {
        int threadCount = 5;
        String base = "http://localhost:" + port;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + workerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    EwaRequestDto requestDto = new EwaRequestDto(employmentId, BigDecimal.valueOf(300), UUID.randomUUID().toString());
                    HttpEntity<EwaRequestDto> request = new HttpEntity<>(requestDto, headers);
                    ResponseEntity<EwaResponseDto> response = restTemplate.postForEntity(
                            base + "/api/ewa-requests/request", request, EwaResponseDto.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            }));
        }

        latch.countDown();
        for (Future<?> future : futures) {
            try { future.get(); } catch (Exception ignored) {}
        }
        executor.shutdown();

        assertEquals(1, successCount.get());
    }
}
