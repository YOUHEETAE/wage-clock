package com.wageclock.wageclock.domain.ewarequest;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransfer;
import com.wageclock.wageclock.domain.ewatransfer.EwaTransferRepository;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.wageclock.wageclock.domain.port.WageTransferResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class EwaIntegrationTest extends IntegrationTestBase {

    @Autowired EwaTransferRepository ewaTransferRepository;

    private String workerToken;
    private String employerToken;
    private Long employmentId;

    @AfterEach
    void tearDown() {
        ewaTransferRepository.deleteAll();
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

    @Test
    void 정상_EWA_요청() {
        EwaRequestDto requestDto = new EwaRequestDto(employmentId, BigDecimal.valueOf(100), UUID.randomUUID().toString());
        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/request",
                new HttpEntity<>(requestDto, authHeaders(workerToken)),
                EwaResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().ewaRequestId());
        assertEquals(EwaRequest.EwaRequestStatus.PENDING, response.getBody().status());
    }

    @Test
    void 한도_초과_EWA_요청_실패() {
        EwaRequestDto requestDto = new EwaRequestDto(employmentId, BigDecimal.valueOf(10000), UUID.randomUUID().toString());
        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/request",
                new HttpEntity<>(requestDto, authHeaders(workerToken)),
                Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void 멱등성_키_중복_요청_실패() {
        String key = UUID.randomUUID().toString();
        testRestTemplate.postForEntity("/api/ewa-requests/request",
                new HttpEntity<>(new EwaRequestDto(employmentId, BigDecimal.valueOf(100), key), authHeaders(workerToken)), EwaResponseDto.class);

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/request",
                new HttpEntity<>(new EwaRequestDto(employmentId, BigDecimal.valueOf(100), key), authHeaders(workerToken)),
                Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void 정상_EWA_승인() {
        when(wageTransferPort.transfer(any(), any(), any())).thenReturn(new WageTransferResult("TX-001", null, null));
        Long ewaId = requestEwa(BigDecimal.valueOf(100));

        ResponseEntity<InitiateEwaResponse> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, authHeaders(employerToken)),
                InitiateEwaResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, response.getBody().status());
    }

    @Test
    void 정상_EWA_거절() {
        Long ewaId = requestEwa(BigDecimal.valueOf(100));

        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/" + ewaId + "/reject",
                new HttpEntity<>(null, authHeaders(employerToken)),
                EwaResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EwaRequest.EwaRequestStatus.REJECTED, response.getBody().status());
    }

    @Test
    void 다른_고용주_승인_시도_실패() {
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        signUp("다른사장", "other@test.com", UserRole.EMPLOYER);
        String otherToken = login("other@test.com");

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, authHeaders(otherToken)),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void PENDING_아닌_요청_initiate_실패() {
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/reject",
                new HttpEntity<>(null, authHeaders(employerToken)), EwaResponseDto.class);

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, authHeaders(employerToken)),
                Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void EWA_APPROVE_검증() {
        when(wageTransferPort.transfer(any(), any(), any())).thenReturn(new WageTransferResult("TX-001", null, null));
        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, authHeaders(employerToken)), InitiateEwaResponse.class);

        EwaRequest.EwaRequestStatus status = ewaRequestRepository.findById(ewaId).get().getStatus();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, status);

        List<EwaTransfer> transfers = ewaTransferRepository.findAll();
        assertEquals(1, transfers.size());
        assertEquals(EwaTransfer.EwaTransferStatus.COMPLETED, transfers.get(0).getStatus());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(transfers.get(0).getAmount()));
    }

    @Test
    void 거절_후_한도_복구_재요청_성공() {
        Long ewaId = requestEwa(BigDecimal.valueOf(500));
        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/reject",
                new HttpEntity<>(null, authHeaders(employerToken)), Void.class);

        Long newEwaId = requestEwa(BigDecimal.valueOf(500));
        assertNotNull(newEwaId);
    }
}
