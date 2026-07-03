package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestDto;
import com.wageclock.wageclock.domain.ewarequest.EwaResponseDto;
import com.wageclock.wageclock.domain.ewarequest.InitiateEwaResponse;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxEvent;
import com.wageclock.wageclock.domain.outbox.EwaTransferFailureOutBoxRepository;
import com.wageclock.wageclock.domain.outbox.OutBoxScheduler;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.infrastructure.InterBankFailureNotification;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class EwaTransferIntegrationTest extends IntegrationTestBase {

    @Autowired EwaTransferRepository ewaTransferRepository;
    @Autowired EwaTransferFailureOutBoxRepository ewaTransferFailureOutBoxRepository;
    @Autowired EwaTransferScheduler ewaTransferScheduler;
    @Autowired OutBoxScheduler outBoxScheduler;

    private String workerToken;
    private String employerToken;
    private Long employmentId;

    @AfterEach
    void tearDown() {
        ewaTransferFailureOutBoxRepository.deleteAll();
        ewaTransferRepository.deleteAll();
        cleanCommon();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        signUp("김사장", "employer@test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@test.com", UserRole.WORKER);
        employerToken = login("employer@test.com");
        workerToken = login("worker@test.com");
        employmentId = createEmployment("worker@test.com", BigDecimal.valueOf(3_600_000), "테스트 사업장", employerToken);
        Long sessionId = clockIn(employmentId, workerToken);
        Thread.sleep(2000);
        clockOut(sessionId, workerToken);
    }

    private Long requestEwa(BigDecimal amount) {
        ResponseEntity<EwaResponseDto> response = testRestTemplate.postForEntity(
                "/api/ewa-requests/request",
                new HttpEntity<>(new EwaRequestDto(employmentId, amount, UUID.randomUUID().toString()), authHeaders(workerToken)),
                EwaResponseDto.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().ewaRequestId();
    }

    private Long initiateEwa(Long ewaId) {
        testRestTemplate.postForEntity(
                "/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, authHeaders(employerToken)),
                InitiateEwaResponse.class);
        return ewaTransferRepository.findAll().get(0).getId();
    }

    // ─── 정상 이체 ───────────────────────────────────────────────────────────────

    @Test
    void initiateEwa_성공_EwaTransfer_COMPLETED_EwaRequest_APPROVED() {
        when(wageTransferPort.prepareTransfer(any())).thenReturn("TX-001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, authHeaders(employerToken)), InitiateEwaResponse.class);

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.COMPLETED, transfer.getStatus());
        assertEquals("TX-001", transfer.getMessageNo());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(transfer.getAmount()));
    }

    // ─── VTIM (타행이체불능) ─────────────────────────────────────────────────────

    @Test
    void initiateEwa_VTIM_EwaTransfer_PENDING_INQUIRY() {
        when(wageTransferPort.prepareTransfer(any())).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, authHeaders(employerToken)), InitiateEwaResponse.class);

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.PENDING, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.PENDING_INQUIRY, transfer.getStatus());
        assertEquals("MSG-001", transfer.getMessageNo());
    }

    @Test
    void 스케줄러_VTIM_재조회_COMPLETED() {
        when(wageTransferPort.prepareTransfer(any())).thenReturn("MSG-001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, authHeaders(employerToken)), InitiateEwaResponse.class);

        assertEquals(EwaTransfer.EwaTransferStatus.PENDING_INQUIRY,
                ewaTransferRepository.findAll().get(0).getStatus());

        when(wageTransferPort.inquireTransfer(any()))
                .thenReturn(new WageTransferResult("MSG-001", null, null));
        ewaTransferScheduler.retryPendingInquiryTransfer();

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.COMPLETED, transfer.getStatus());
    }

    // ─── 예외 → UNKNOWN ──────────────────────────────────────────────────────────

    @Test
    void initiateEwa_예외발생_EwaTransfer_UNKNOWN() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, authHeaders(employerToken)), InitiateEwaResponse.class);

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.UNKNOWN, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.UNKNOWN, transfer.getStatus());
    }

    @Test
    void 스케줄러_UNKNOWN_재조회_COMPLETED() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        testRestTemplate.postForEntity("/api/ewa-requests/" + ewaId + "/initiate",
                new HttpEntity<>(null, authHeaders(employerToken)), InitiateEwaResponse.class);

        assertEquals(EwaTransfer.EwaTransferStatus.UNKNOWN,
                ewaTransferRepository.findAll().get(0).getStatus());

        when(wageTransferPort.inquireTransfer(any()))
                .thenReturn(new WageTransferResult("TX-001", null, null));
        ewaTransferScheduler.retryPendingInquiryTransfer();

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());
        assertEquals(EwaTransfer.EwaTransferStatus.COMPLETED,
                ewaTransferRepository.findAll().get(0).getStatus());
    }

    // ─── 타행이체불능 소켓 수신 ──────────────────────────────────────────────────

    @Test
    void 타행이체불능_수신_FAILED_OutBox_생성() {
        when(wageTransferPort.prepareTransfer(any())).thenReturn("1TX001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("1TX001", null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        initiateEwa(ewaId);

        ResponseEntity<Void> response = testRestTemplate.postForEntity(
                "/mock/firm-banking/3000",
                new HttpEntity<>(new InterBankFailureNotification("1TX001"), new HttpHeaders()),
                Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.RETRYING, transfer.getStatus());

        List<EwaTransferFailureOutBoxEvent> events = ewaTransferFailureOutBoxRepository.findAll();
        assertEquals(1, events.size());
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PENDING, events.get(0).getStatus());
        assertEquals("1TX001", events.get(0).getMessageNo());
    }

    // ─── OutBox 재이체 ────────────────────────────────────────────────────────────

    @Test
    void OutBox_재이체_성공_COMPLETED() {
        when(wageTransferPort.prepareTransfer(any())).thenReturn("1TX001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("1TX001", null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        initiateEwa(ewaId);

        testRestTemplate.postForEntity(
                "/mock/firm-banking/3000",
                new HttpEntity<>(new InterBankFailureNotification("1TX001"), new HttpHeaders()),
                Void.class);

        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PENDING,
                ewaTransferFailureOutBoxRepository.findAll().get(0).getStatus());

        when(wageTransferPort.prepareTransfer(any())).thenReturn("1TX002");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("1TX002", null, null));
        outBoxScheduler.processEwaTransferFailureOutBoxEvent();

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.COMPLETED, transfer.getStatus());
        assertEquals("1TX002", transfer.getMessageNo());

        EwaTransferFailureOutBoxEvent event = ewaTransferFailureOutBoxRepository.findAll().get(0);
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PROCESSED, event.getStatus());
    }

    @Test
    void OutBox_재이체_확정실패_FAILED_EwaRequest_APPROVED_유지() {
        when(wageTransferPort.prepareTransfer(any())).thenReturn("1TX001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("1TX001", null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        initiateEwa(ewaId);

        testRestTemplate.postForEntity(
                "/mock/firm-banking/3000",
                new HttpEntity<>(new InterBankFailureNotification("1TX001"), new HttpHeaders()),
                Void.class);

        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult(null, null, "계좌 없음"));
        outBoxScheduler.processEwaTransferFailureOutBoxEvent();

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.FAILED, transfer.getStatus());
    }

    @Test
    void OutBox_재이체_실패_retryCount_증가() {
        when(wageTransferPort.prepareTransfer(any())).thenReturn("1TX001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("1TX001", null, null));

        Long ewaId = requestEwa(BigDecimal.valueOf(100));
        initiateEwa(ewaId);

        testRestTemplate.postForEntity(
                "/mock/firm-banking/3000",
                new HttpEntity<>(new InterBankFailureNotification("1TX001"), new HttpHeaders()),
                Void.class);

        when(wageTransferPort.transfer(any(), any(), any()))
                .thenThrow(new RuntimeException("재이체 실패"));
        outBoxScheduler.processEwaTransferFailureOutBoxEvent();

        EwaTransferFailureOutBoxEvent event = ewaTransferFailureOutBoxRepository.findAll().get(0);
        assertEquals(1, event.getRetryCount());
        assertEquals(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus.PENDING, event.getStatus());

        EwaTransfer transfer = ewaTransferRepository.findAll().get(0);
        assertEquals(EwaTransfer.EwaTransferStatus.UNKNOWN, transfer.getStatus());

        EwaRequest ewaRequest = ewaRequestRepository.findById(ewaId).get();
        assertEquals(EwaRequest.EwaRequestStatus.APPROVED, ewaRequest.getStatus());
    }
}
