package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.port.*;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BulkSettlementService {

    private final BulkSettlementProcessor bulkSettlementProcessor;
    private final VirtualAccountPort virtualAccountPort;
    private final EmployerRepository employerRepository;
    private final WageTransferPort wageTransferPort;
    private final WorkerRepository workerRepository;
    private final ExecutorService settlementExecutor;

    public BulkSettlementService(BulkSettlementProcessor bulkSettlementProcessor,
                                 VirtualAccountPort virtualAccountPort,
                                 EmployerRepository employerRepository,
                                 WageTransferPort wageTransferPort,
                                 WorkerRepository workerRepository,
                                 ExecutorService settlementExecutor
    ) {
        this.bulkSettlementProcessor = bulkSettlementProcessor;
        this.virtualAccountPort = virtualAccountPort;
        this.employerRepository = employerRepository;
        this.wageTransferPort = wageTransferPort;
        this.workerRepository = workerRepository;
        this.settlementExecutor = settlementExecutor;
    }

    public BulkSettlementResponse requestBulkSettlement(List<Long> employmentIds, Long employerId) {
        Employer employer = employerRepository.findById(employerId)
                .orElseThrow(() -> new NotFoundException("Employer not found"));
        BulkSettlement bulkSettlement = bulkSettlementProcessor.createBulkSettlement(employmentIds, employerId);
        VirtualAccountResult account = virtualAccountPort.issueVirtualAccount(bulkSettlement.getPortOnePaymentId(),
                bulkSettlement.getTotalAmount(), "BULK-" + bulkSettlement.getId(), employer.getName());
        bulkSettlementProcessor.updateAccountInfo(account, bulkSettlement);
        return new BulkSettlementResponse(bulkSettlement.getId(), bulkSettlement.getTotalAmount(), account.bank(),
                account.accountNumber(), account.expiredAt());
    }

    /**
     * 결제 상태를 PG에 재조회해 정산 진행 여부를 판단한다.
     * 웹훅과 스케줄러 양쪽의 진입점이며, 웹훅 페이로드는 "확인해보라"는 신호로만 쓴다.
     * 위조된 웹훅이 와도 PG가 PAID로 답하지 않으면 아무 일도 일어나지 않는다.
     */
    public void syncPaymentStatus(String portOnePaymentId) {
        VirtualAccountPaymentResult payment = virtualAccountPort.getPaymentResult(portOnePaymentId);

        switch (payment.status()) {
            case PAID -> {
                BigDecimal expected = bulkSettlementProcessor.getTotalAmount(portOnePaymentId);
                if (payment.paidAmount() == null || expected.compareTo(payment.paidAmount()) != 0) {
                    log.error("결제 금액 불일치로 정산 중단 paymentId={} 기대={} 실제={}",
                            portOnePaymentId, expected, payment.paidAmount());
                    return;
                }
                // 웹훅 재전송·스케줄러와 동시에 들어올 수 있으므로 선점한 쪽만 이체한다
                if(!bulkSettlementProcessor.claimForTransfer(portOnePaymentId)){
                    return;
                }
                initiateBulkSettlement(portOnePaymentId);
            }
            case FAILED -> failedPayment(portOnePaymentId);
            case PENDING -> { } // 아직 입금 전 — 다음 확인을 기다린다
        }
    }

    /**
     * PENDING 아이템을 이체한다.
     * <p>
     * 호출 전에 {@code claimForTransfer}로 선점되어 있어야 한다. 이 메서드 자체는 선점하지 않으므로,
     * 새 진입점을 추가할 때는 진입점 쪽에서 claim해야 한다 (현재 진입점: syncPaymentStatus, retrySettlement).
     * <p>
     * 이체할 아이템이 없거나 완료되지 않은 아이템이 남으면 TRANSFER_FAILED로 떨어뜨린다.
     * 여기서 상태를 확정하지 않으면 TRANSFERRING에 갇혀 어느 스케줄러도 잡지 못한다.
     */
    public void initiateBulkSettlement(String portOnePaymentId) {
        BulkSettlementContext contexts = bulkSettlementProcessor.loadItemContexts(portOnePaymentId);
        if (contexts.bulkSettlementItemContexts().isEmpty()) {
            if (!bulkSettlementProcessor.completeSettlement(portOnePaymentId)) {
                bulkSettlementProcessor.transferFailSettlement(portOnePaymentId);
            }
            return;
        }
        List<Long> workerIds = contexts.bulkSettlementItemContexts().stream()
                .map(BulkSettlementItemContext::workerId).toList();
        Map<Long, Worker> workerMap = workerRepository.findAllById(workerIds).stream()
                .collect(Collectors.toMap(Worker::getId, Function.identity()));

        List<CompletableFuture<TransferItemResult>> futures = contexts.bulkSettlementItemContexts().stream()
                .map(context -> CompletableFuture.<TransferItemResult>supplyAsync(() ->
                                processItem(context, workerMap), settlementExecutor)
                        .orTimeout(30, TimeUnit.SECONDS)
                        .exceptionally(e -> handleTransferException(e, context.itemId()))).toList();
        List<TransferItemResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        results.forEach(this::applyResult);
        if (!bulkSettlementProcessor.completeSettlement(portOnePaymentId)) {
            bulkSettlementProcessor.transferFailSettlement(portOnePaymentId);
        }
    }

    /**
     * TRANSFER_FAILED 정산을 재처리한다. 결과가 미확정인 아이템은 조회로 확정하고,
     * 아직 나가지 않은 아이템은 재이체한다.
     * <p>
     * 조회 단계까지 선점 안에 두어야 한다. 조회 중에 다른 진입점이 들어와 재이체를 시작하면
     * 같은 아이템에 조회와 이체가 겹친다.
     */
    public void retrySettlement(String portOnePaymentId) {
        if(!bulkSettlementProcessor.claimForTransfer(portOnePaymentId)){
            return;
        }
        // PENDING_INQUIRY 아이템 먼저 재조회 — 기존 messageNo로 결과를 확인한다
        BulkSettlementContext inquiryContexts = bulkSettlementProcessor.loadPendingInquiryContexts(portOnePaymentId);
        List<CompletableFuture<TransferItemResult>> inquiryFutures = inquiryContexts.bulkSettlementItemContexts().stream()
                .map(context -> CompletableFuture.<TransferItemResult>supplyAsync(() -> {
                    WageTransferResult result = wageTransferPort.inquireTransfer(context.messageNo());
                    return toTransferItemResult(result, context);
                }, settlementExecutor
                ).orTimeout(30, TimeUnit.SECONDS)
                        .exceptionally(e -> handleTransferException(e, context.itemId()))).toList();
        List<TransferItemResult> results = inquiryFutures.stream()
                .map(CompletableFuture::join)
                .toList();

        results.forEach(this::applyResult);
        // PENDING 아이템 재이체
        initiateBulkSettlement(portOnePaymentId);
    }

     public void failedPayment(String portOnePaymentId){
        bulkSettlementProcessor.failPayment(portOnePaymentId);
     }
     public void receiveInterBankFailure(String transferId){
        bulkSettlementProcessor.receiveInterBankFailure(transferId);
     }

     //캡슐화 메서드

    /**
     * 이체할 때마다 새 전문번호를 발급한다. 기존 번호를 재사용하지 않는 이유는,
     * 이미 은행에 전송된 번호라면 재이체가 아니라 조회(7000/100)로 결과를 확인해야 하기 때문이다.
     * <p>
     * 그래서 호출부는 발급 실패와 이체 실패를 분리해 처리한다.
     * 발급 실패는 은행에 요청이 나가지 않은 상태이므로 새 번호로 재시도하고(Retryable),
     * 이체 실패는 번호가 이미 나갔을 수 있으므로 그 번호로 조회한다(UNKNOWN → PENDING_INQUIRY).
     * 두 경로를 한 try-catch로 묶으면 발급 실패한 건이 없는 번호로 조회를 시도하게 된다.
     */
    private String issueTransferMessageNo(BulkSettlementItemContext context) {
        String messageNo = wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT);
        bulkSettlementProcessor.assignMessageNo(context.itemId(), messageNo);
        return messageNo;
    }
    private TransferItemResult toTransferItemResult(WageTransferResult result, BulkSettlementItemContext context) {
        if (result.transferId() != null) {
            return new TransferItemResult.Success(context.itemId(), result.transferId());
        }else if(result.pendingMessageNo() != null) {

            return new TransferItemResult.PendingInquiry(context.itemId(), result.pendingMessageNo());
        } else if (result.failureReason() != null) {
            return new TransferItemResult.Fail(context.itemId(), result.failureReason());
            //todo : 확정 실패시 알림 발송 필요
        }
        return new TransferItemResult.Unknown(context.itemId());
    }
    private void applyResult(TransferItemResult result){
        switch (result) {
            case TransferItemResult.Success s ->
                bulkSettlementProcessor.completeItem(s.itemId());
            case TransferItemResult.PendingInquiry p ->
                bulkSettlementProcessor.markPendingInquiry(p.itemId());
            case TransferItemResult.Fail f ->
                bulkSettlementProcessor.failItem(f.itemId());
            case TransferItemResult.Unknown u ->
                bulkSettlementProcessor.unknownItem(u.itemId());
            case TransferItemResult.Retryable r -> {}
        }
    }
    private TransferItemResult processItem(BulkSettlementItemContext context,
                                           Map<Long, Worker> workerMap){
        Worker worker = workerMap.get(context.workerId());
        if (worker == null) {
            log.error("Worker not found itemId={}, workerId={}", context.itemId(), context.workerId());
            return new TransferItemResult.Fail(context.itemId(), "Worker not found");
        }
        String messageNo;
        try {
            messageNo = issueTransferMessageNo(context);
        }catch (Exception e){
            log.error("messageNo 발급/저장 실패 itemId={}", context.itemId(), e);
            //todo : 아웃박스 재시도 소진(MAX_RETRY 초과 → FAILED) 시 운영팀 알림 필요
            return new TransferItemResult.Retryable(context.itemId());
        }
        WageTransferResult result = wageTransferPort.transfer(worker, context.amount(), messageNo);
        return toTransferItemResult(result, context);
    }
    private TransferItemResult handleTransferException(Throwable e, Long itemId) {
        if (e instanceof TimeoutException || e.getCause() instanceof TimeoutException) {
            log.error("이체 조회 타임아웃 itemId={}", itemId, e);
        } else {
            log.error("이체 처리 중 예외 itemId={}", itemId, e);
        }
        return new TransferItemResult.Unknown(itemId);
    }
}
