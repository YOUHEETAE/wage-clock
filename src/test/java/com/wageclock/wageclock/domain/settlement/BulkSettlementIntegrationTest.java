package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.ewarequest.EwaRequestDto;
import com.wageclock.wageclock.domain.ewarequest.EwaResponseDto;
import com.wageclock.wageclock.domain.outbox.*;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodSummaryResponse;
import com.wageclock.wageclock.domain.worksession.ClockInRequest;
import com.wageclock.wageclock.domain.port.PaymentStatus;
import com.wageclock.wageclock.domain.port.VirtualAccountPaymentResult;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.infrastructure.InterBankFailureNotification;
import com.wageclock.wageclock.infrastructure.PortOneWebhookPayload;
import com.wageclock.wageclock.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BulkSettlementIntegrationTest extends IntegrationTestBase {

    @Autowired BulkSettlementRepository bulkSettlementRepository;
    @Autowired BulkSettlementItemRepository bulkSettlementItemRepository;
    @Autowired BulkSettlementOutBoxEventRepository bulkSettlementOutBoxEventRepository;
    @Autowired InterBankFailureOutBoxEventRepository interBankFailureOutBoxEventRepository;
    @Autowired OutBoxScheduler outBoxScheduler;
    @Autowired BulkSettlementScheduler bulkSettlementScheduler;
    @Autowired BulkSettlementProcessor bulkSettlementProcessor;
    @Autowired JdbcTemplate jdbcTemplate;

    String employerToken;
    String workerToken;
    Long employmentId;
    Long workplaceId;

    @BeforeEach
    void setUp() throws InterruptedException {
        signUp("김사장", "employer@bulk-test.com", UserRole.EMPLOYER);
        signUp("박사원", "worker@bulk-test.com", UserRole.WORKER);
        employerToken = login("employer@bulk-test.com");
        workerToken = login("worker@bulk-test.com");
        workplaceId = createWorkplace("테스트 사업장", employerToken);
        employmentId = createEmployment("worker@bulk-test.com", BigDecimal.valueOf(3_600_000), workplaceId, employerToken);
        Long sessionId = clockIn(employmentId, workerToken);
        Thread.sleep(2000);
        clockOut(sessionId, workerToken);
    }

    @AfterEach
    void tearDown() {
        interBankFailureOutBoxEventRepository.deleteAll();
        bulkSettlementOutBoxEventRepository.deleteAll();
        bulkSettlementRepository.deleteAll();
        cleanCommon();
    }

    private Long setupSecondWorker() throws InterruptedException {
        signUp("이직원", "worker2@test.com", UserRole.WORKER);
        String workerToken2 = login("worker2@test.com");
        Long employmentId2 = createEmployment("worker2@test.com", BigDecimal.valueOf(3_600_000), workplaceId, employerToken);
        Long sessionId2 = clockIn(employmentId2, workerToken2);
        Thread.sleep(2000);
        clockOut(sessionId2, workerToken2);
        return employmentId2;
    }

    /**
     * 웹훅은 "확인해보라"는 신호일 뿐이고 실제 판단은 PG 재조회로 하므로,
     * 조회 결과를 PAID + 정산 총액과 일치하는 금액으로 세팅한 뒤 웹훅을 보낸다.
     */
    private void triggerPaidWebhook(String portOnePaymentId) {
        BigDecimal total = bulkSettlementRepository.findByPortOnePaymentId(portOnePaymentId)
                .orElseThrow()
                .getTotalAmount();
        when(virtualAccountPort.getPaymentResult(portOnePaymentId))
                .thenReturn(new VirtualAccountPaymentResult(PaymentStatus.PAID, total));
        postWebhook(portOnePaymentId);
    }

    private void postWebhook(String portOnePaymentId) {
        testRestTemplate.postForEntity("/webhook",
                new HttpEntity<>(new PortOneWebhookPayload("Transaction.Paid", null,
                        new PortOneWebhookPayload.Data(null, portOnePaymentId, null)), new HttpHeaders()),
                Void.class);
    }

    private String requestSettlement() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)),
                BulkSettlementResponse.class);
        return bulkSettlementRepository.findAll().get(0).getPortOnePaymentId();
    }

    private void requestAndTriggerSettlement(List<Long> employmentIds) {
        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(employmentIds, authHeaders(employerToken)),
                BulkSettlementResponse.class);
        String portOnePaymentId = bulkSettlementRepository.findAll().get(0).getPortOnePaymentId();
        triggerPaidWebhook(portOnePaymentId);
    }

    /** 이체가 시작된 지 오래된 것처럼 만든다. updatedAt은 JPA 감사 필드라 직접 쓸 수 없다. */
    private void backdateUpdatedAt(String portOnePaymentId, int minutes) {
        jdbcTemplate.update("UPDATE bulk_settlements SET updated_at = ? WHERE port_one_payment_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(minutes)), portOnePaymentId);
    }

    // ─── 중복 진입 차단 ───────────────────────────────────────────────────────────
    // 웹훅·스케줄러가 같은 정산에 동시에 들어올 수 있으므로 선점한 쪽만 이체한다.

    @Test
    void 이미_선점된_정산은_다시_선점되지_않는다() {
        String portOnePaymentId = requestSettlement();

        assertTrue(bulkSettlementProcessor.claimForTransfer(portOnePaymentId));
        assertFalse(bulkSettlementProcessor.claimForTransfer(portOnePaymentId));
    }

    @Test
    void 완료된_정산은_선점되지_않는다() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("1TX001", null, null));
        String portOnePaymentId = requestSettlement();
        triggerPaidWebhook(portOnePaymentId);

        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED,
                bulkSettlementRepository.findAll().get(0).getStatus());
        assertFalse(bulkSettlementProcessor.claimForTransfer(portOnePaymentId));
    }

    @Test
    void 중복_웹훅이_와도_이체는_한_번만_실행된다() {
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("1TX001", null, null));
        String portOnePaymentId = requestSettlement();

        triggerPaidWebhook(portOnePaymentId);
        triggerPaidWebhook(portOnePaymentId);   // PortOne 재전송

        verify(wageTransferPort, times(1)).transfer(any(), any(), any());
        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED,
                bulkSettlementRepository.findAll().get(0).getStatus());
    }

    @Test
    void 이체중인_정산은_스케줄러가_다시_집지_않는다() {
        String portOnePaymentId = requestSettlement();
        bulkSettlementProcessor.claimForTransfer(portOnePaymentId);   // 이체 진행 중

        bulkSettlementScheduler.retryMissedWebhook();

        // PROCESSING만 훑으므로 TRANSFERRING은 대상이 아니다
        verify(virtualAccountPort, never()).getPaymentResult(portOnePaymentId);
        assertEquals(BulkSettlement.BulkSettlementStatus.TRANSFERRING,
                bulkSettlementRepository.findAll().get(0).getStatus());
    }

    // ─── 이체 중 방치 회수 ────────────────────────────────────────────────────────
    // 프로세스가 죽으면 TRANSFERRING인 채로 남고 어느 스케줄러도 잡지 못한다.

    @Test
    void 이체중_상태로_방치되면_회수된다() {
        String portOnePaymentId = requestSettlement();
        Long itemId = bulkSettlementItemRepository.findAll().get(0).getId();

        // 이체를 시작하고 전문번호까지 발급한 직후 프로세스가 죽은 상황
        bulkSettlementProcessor.claimForTransfer(portOnePaymentId);
        bulkSettlementProcessor.assignMessageNo(itemId, "1TX001");
        backdateUpdatedAt(portOnePaymentId, 31);

        bulkSettlementScheduler.recoverStaleTransferring();

        assertEquals(BulkSettlement.BulkSettlementStatus.TRANSFER_FAILED,
                bulkSettlementRepository.findAll().get(0).getStatus());
        // 전문번호가 이미 나갔을 수 있으므로 재이체가 아니라 조회 경로로 보낸다
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.UNKNOWN,
                bulkSettlementItemRepository.findById(itemId).orElseThrow().getStatus());
    }

    @Test
    void 전문번호가_없는_아이템은_회수해도_재이체_대상으로_남는다() {
        String portOnePaymentId = requestSettlement();
        Long itemId = bulkSettlementItemRepository.findAll().get(0).getId();

        // 전문번호 발급 전에 죽었다면 은행에 아무것도 나가지 않았다
        bulkSettlementProcessor.claimForTransfer(portOnePaymentId);
        backdateUpdatedAt(portOnePaymentId, 31);

        bulkSettlementScheduler.recoverStaleTransferring();

        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.PENDING,
                bulkSettlementItemRepository.findById(itemId).orElseThrow().getStatus());
    }

    @Test
    void 임계시간_전이면_회수하지_않는다() {
        String portOnePaymentId = requestSettlement();
        bulkSettlementProcessor.claimForTransfer(portOnePaymentId);

        bulkSettlementScheduler.recoverStaleTransferring();

        assertEquals(BulkSettlement.BulkSettlementStatus.TRANSFERRING,
                bulkSettlementRepository.findAll().get(0).getStatus());
    }

    // ─── 웹훅 검증 ────────────────────────────────────────────────────────────────
    // 웹훅 페이로드는 신뢰하지 않고 PG 재조회로 판단한다.

    @Test
    void 입금되지_않았는데_웹훅이_오면_정산이_시작되지_않는다() {
        String portOnePaymentId = requestSettlement();

        // 위조된 Transaction.Paid 웹훅. PG는 아직 입금 전이라고 답한다.
        when(virtualAccountPort.getPaymentResult(portOnePaymentId))
                .thenReturn(new VirtualAccountPaymentResult(PaymentStatus.PENDING, null));
        postWebhook(portOnePaymentId);

        assertEquals(BulkSettlement.BulkSettlementStatus.PROCESSING,
                bulkSettlementRepository.findAll().get(0).getStatus());
        verify(wageTransferPort, never()).transfer(any(), any(), any());
    }

    @Test
    void 입금액이_정산총액과_다르면_정산이_시작되지_않는다() {
        String portOnePaymentId = requestSettlement();

        when(virtualAccountPort.getPaymentResult(portOnePaymentId))
                .thenReturn(new VirtualAccountPaymentResult(PaymentStatus.PAID, BigDecimal.ONE));
        postWebhook(portOnePaymentId);

        assertEquals(BulkSettlement.BulkSettlementStatus.PROCESSING,
                bulkSettlementRepository.findAll().get(0).getStatus());
        verify(wageTransferPort, never()).transfer(any(), any(), any());
    }

    @Test
    void 결제가_취소되면_PAYMENT_FAILED() {
        String portOnePaymentId = requestSettlement();

        when(virtualAccountPort.getPaymentResult(portOnePaymentId))
                .thenReturn(new VirtualAccountPaymentResult(PaymentStatus.FAILED, null));
        postWebhook(portOnePaymentId);

        assertEquals(BulkSettlement.BulkSettlementStatus.PAYMENT_FAILED,
                bulkSettlementRepository.findAll().get(0).getStatus());
        verify(wageTransferPort, never()).transfer(any(), any(), any());
    }

    // ─── 정산 중 PayPeriod 잠금(SETTLING) ────────────────────────────────────────
    // 정산 시작과 마감 사이는 사장 입금을 기다리는 긴 구간이다. 그동안 PayPeriod 금액이
    // 움직이면 이미 확정된 이체액과 어긋나므로, SETTLING으로 잠가 변동을 막는다.

    @Test
    void 정산을_요청하면_PayPeriod가_SETTLING이_된다() {
        requestSettlement();

        assertEquals(PayPeriod.PayPeriodStatus.SETTLING,
                payPeriodRepository.findAll().get(0).getStatus());
    }

    // 출근을 허용하면 세션이 SETTLING인 PayPeriod에 붙고, 마감 뒤 퇴근한 적립액이 고아가 된다
    @Test
    void 정산_진행중에는_출근할_수_없다() {
        requestSettlement();

        ResponseEntity<Void> response = testRestTemplate.postForEntity("/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), authHeaders(workerToken)), Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(1, payPeriodRepository.findAll().size());   // 새 PayPeriod가 생기지 않는다
    }

    @Test
    void 결제가_취소되면_PayPeriod가_ACTIVE로_돌아온다() {
        String portOnePaymentId = requestSettlement();

        when(virtualAccountPort.getPaymentResult(portOnePaymentId))
                .thenReturn(new VirtualAccountPaymentResult(PaymentStatus.FAILED, null));
        postWebhook(portOnePaymentId);

        assertEquals(PayPeriod.PayPeriodStatus.ACTIVE,
                payPeriodRepository.findAll().get(0).getStatus());
    }

    // 되돌린 뒤에는 출근도 재정산도 다시 된다 — SETTLING에 갇히지 않는다
    @Test
    void 결제_취소로_되돌아오면_출근할_수_있다() {
        String portOnePaymentId = requestSettlement();
        when(virtualAccountPort.getPaymentResult(portOnePaymentId))
                .thenReturn(new VirtualAccountPaymentResult(PaymentStatus.FAILED, null));
        postWebhook(portOnePaymentId);

        ResponseEntity<Void> response = testRestTemplate.postForEntity("/api/work-sessions/clock-in",
                new HttpEntity<>(new ClockInRequest(employmentId), authHeaders(workerToken)), Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ─── 정산 진입 검증 ──────────────────────────────────────────────────────────

    @Test
    void 근무중인_근로자가_있으면_정산_요청이_거부된다() {
        clockIn(employmentId, workerToken);

        ResponseEntity<Void> response = testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)), Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(bulkSettlementRepository.findAll().isEmpty());
        assertEquals(PayPeriod.PayPeriodStatus.ACTIVE,
                payPeriodRepository.findAll().get(0).getStatus());
    }

    // 정산 후 EWA가 확정되면 totalEwaAmount가 바뀌어 이미 이체한 금액과 어긋난다
    @Test
    void 미확정_EWA_요청이_있으면_정산_요청이_거부된다() {
        testRestTemplate.postForEntity("/api/ewa-requests/request",
                new HttpEntity<>(new EwaRequestDto(employmentId, BigDecimal.valueOf(500),
                        UUID.randomUUID().toString()), authHeaders(workerToken)),
                EwaResponseDto.class);

        ResponseEntity<Void> response = testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)), Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(bulkSettlementRepository.findAll().isEmpty());
    }

    // ─── 정산 중 조회 ────────────────────────────────────────────────────────────
    // SETTLING을 조회에서 빼면 정산 거는 순간 화면이 비어버린다.

    @Test
    void 정산_진행중에도_근로자_summary가_조회된다() {
        requestSettlement();

        ResponseEntity<PayPeriodSummaryResponse> response = testRestTemplate.exchange(
                "/api/pay-periods/" + employmentId + "/summary", HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(workerToken)), PayPeriodSummaryResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(PayPeriod.PayPeriodStatus.SETTLING, response.getBody().payPeriodStatus());
    }

    @Test
    void 정산_진행중에도_사장_summaries에_잡히고_상태가_노출된다() {
        requestSettlement();

        ResponseEntity<List<PayPeriodSummaryResponse>> response = testRestTemplate.exchange(
                "/api/pay-periods/summaries?workplaceId=" + workplaceId, HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(employerToken)),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(PayPeriod.PayPeriodStatus.SETTLING, response.getBody().get(0).payPeriodStatus());
    }

    @Test
    void bulkSettlementRequest_정상_BulkSettlement_PROCESSING_Outbox_PROCESSED() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));

        ResponseEntity<BulkSettlementResponse> response = testRestTemplate.postForEntity(
                "/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)),
                BulkSettlementResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.PROCESSING, settlement.getStatus());
        BulkSettlementOutBoxEvent event = bulkSettlementOutBoxEventRepository.findAll().get(0);
        assertEquals(BulkSettlementOutBoxEvent.OutBoxStatus.PROCESSED, event.getStatus());
    }

    @Test
    void initiateBulkSettlement_성공_아이템_COMPLETED_PayPeriod_CLOSED_BulkSettlement_COMPLETED() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null));

        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)),
                BulkSettlementResponse.class);
        String portOnePaymentId = bulkSettlementRepository.findAll().get(0).getPortOnePaymentId();

        triggerPaidWebhook(portOnePaymentId);

        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED, settlement.getStatus());
        BulkSettlementItem item = bulkSettlementItemRepository.findAll().get(0);
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.COMPLETED, item.getStatus());
        PayPeriod payPeriod = payPeriodRepository.findAll().get(0);
        assertEquals(PayPeriod.PayPeriodStatus.CLOSED, payPeriod.getStatus());
    }

    @Test
    void initiateBulkSettlement_VTIM_아이템_PENDING_INQUIRY_BulkSettlement_TRANSFER_FAILED() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)),
                BulkSettlementResponse.class);
        String portOnePaymentId = bulkSettlementRepository.findAll().get(0).getPortOnePaymentId();

        triggerPaidWebhook(portOnePaymentId);

        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.TRANSFER_FAILED, settlement.getStatus());
        BulkSettlementItem item = bulkSettlementItemRepository.findAll().get(0);
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.PENDING_INQUIRY, item.getStatus());
    }

    @Test
    void retrySettlement_PENDING_INQUIRY_조회성공_COMPLETED() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult(null, "MSG-001", null));

        requestAndTriggerSettlement(List.of(employmentId));

        assertEquals(BulkSettlement.BulkSettlementStatus.TRANSFER_FAILED,
                bulkSettlementRepository.findAll().get(0).getStatus());

        when(wageTransferPort.inquireTransfer(any()))
                .thenReturn(new WageTransferResult("TX-001", null, null));
        bulkSettlementScheduler.retryFailedTransfers();

        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED, settlement.getStatus());
        BulkSettlementItem item = bulkSettlementItemRepository.findAll().get(0);
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.COMPLETED, item.getStatus());
    }

    @Test
    void initiateBulkSettlement_다수_아이템_일부_성공_일부_애매함() throws InterruptedException {
        Long employmentId2 = setupSecondWorker();

        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("TX-001", null, null))
                .thenThrow(new RuntimeException("이체 실패"));

        requestAndTriggerSettlement(List.of(employmentId, employmentId2));

        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.TRANSFER_FAILED, settlement.getStatus());

        List<BulkSettlementItem> items = bulkSettlementItemRepository.findAll();
        assertEquals(2, items.size());
        long completedCount = items.stream()
                .filter(i -> i.getStatus() == BulkSettlementItem.BulkSettlementItemStatus.COMPLETED).count();
        long unknownCount = items.stream()
                .filter(i -> i.getStatus() == BulkSettlementItem.BulkSettlementItemStatus.UNKNOWN).count();
        assertEquals(1, completedCount);
        assertEquals(1, unknownCount);
    }

    @Test
    void receiveInterBankFailure_아이템_RETRYING_세틀먼트_RETRYING_Outbox_생성_재이체_성공() {
        when(virtualAccountPort.issueVirtualAccount(any(), any(), any(), any()))
                .thenReturn(new VirtualAccountResult("Toss", "1234-5678", "2026-12-31"));
        when(wageTransferPort.prepareTransfer(any())).thenReturn("2TX001");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("2TX001", null, null));

        testRestTemplate.postForEntity("/api/settlements/request",
                new HttpEntity<>(List.of(employmentId), authHeaders(employerToken)),
                BulkSettlementResponse.class);
        String portOnePaymentId = bulkSettlementRepository.findAll().get(0).getPortOnePaymentId();

        triggerPaidWebhook(portOnePaymentId);

        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED,
                bulkSettlementRepository.findAll().get(0).getStatus());

        ResponseEntity<Void> response = testRestTemplate.postForEntity("/mock/firm-banking/3000",
                new HttpEntity<>(new InterBankFailureNotification("2TX001"), new HttpHeaders()),
                Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        BulkSettlementItem item = bulkSettlementItemRepository.findAll().get(0);
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.RETRYING, item.getStatus());
        BulkSettlement settlement = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.RETRYING, settlement.getStatus());
        InterBankFailureOutBoxEvent event = interBankFailureOutBoxEventRepository.findAll().get(0);
        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.PENDING, event.getStatus());
        assertEquals("2TX001", event.getMessageNo());

        when(wageTransferPort.prepareTransfer(any())).thenReturn("2TX002");
        when(wageTransferPort.transfer(any(), any(), any()))
                .thenReturn(new WageTransferResult("2TX002", null, null));
        outBoxScheduler.processInterBankFailureOutBoxEvent();

        BulkSettlementItem retried = bulkSettlementItemRepository.findAll().get(0);
        assertEquals(BulkSettlementItem.BulkSettlementItemStatus.COMPLETED, retried.getStatus());
        BulkSettlement settledAgain = bulkSettlementRepository.findAll().get(0);
        assertEquals(BulkSettlement.BulkSettlementStatus.COMPLETED, settledAgain.getStatus());
        InterBankFailureOutBoxEvent processed = interBankFailureOutBoxEventRepository.findAll().get(0);
        assertEquals(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus.PROCESSED, processed.getStatus());
    }
}
