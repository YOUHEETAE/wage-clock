package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.port.*;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public void initiateBulkSettlement(String portOnePaymentId) {
        BulkSettlementContext contexts = bulkSettlementProcessor.loadItemContexts(portOnePaymentId);
        if (contexts.bulkSettlementItemContexts().isEmpty()) {
            bulkSettlementProcessor.completeSettlement(portOnePaymentId);
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
        boolean anyNotCompleted = results.stream()
                .anyMatch(r -> !(r instanceof TransferItemResult.Success));
        if (anyNotCompleted) {
            bulkSettlementProcessor.transferFailSettlement(portOnePaymentId);
            return;
        }
        bulkSettlementProcessor.completeSettlement(portOnePaymentId);
    }

    public void retrySettlement(String portOnePaymentId) {
        // PENDING_INQUIRY 아이템 먼저 재조회
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
            return new TransferItemResult.Retryable(context.itemId());
            //todo: 일정 횟수 이상 반복되면 운영팀 알림 필요 (MAX_RETRY 미구현)
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
