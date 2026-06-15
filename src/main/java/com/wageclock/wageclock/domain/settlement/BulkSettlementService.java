package com.wageclock.wageclock.domain.settlement;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.port.VirtualAccountPort;
import com.wageclock.wageclock.domain.port.VirtualAccountResult;
import com.wageclock.wageclock.domain.port.WageTransferPort;
import com.wageclock.wageclock.domain.port.WageTransferResult;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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

    public BulkSettlementResponse bulkSettlementRequest(List<Long> employmentIds, Long employerId) {
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
                .map(context -> CompletableFuture.<TransferItemResult>supplyAsync(() -> {
                                    Worker worker = Optional.ofNullable(workerMap.get(context.workerId()))
                                            .orElseThrow(() -> new NotFoundException("Worker not found"));
                                    WageTransferResult result = wageTransferPort.transfer(worker, context.amount(), "BULK-" + context.itemId());
                                    if (result.transferId() != null) {
                                        return new TransferItemResult.Success(context.itemId(), result.transferId());
                                    }
                                    return new TransferItemResult.PendingInquiry(context.itemId(), result.pendingMessageNo());
                                }, settlementExecutor)
                                .exceptionally(e -> {
                                      log.error("이체 결과 조회 실패 itemId={}", context.itemId(), e);
                                      // TODO: 네트워크 예외 시 이중 송금 방지를 위해 전문번호 사전 생성 후 PendingInquiry 처리 필요
                                      return new TransferItemResult.Fail(context.itemId());
                                })

                ).toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(30, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            log.error("이체 타임아웃", e);
            bulkSettlementProcessor.failedSettlement(portOnePaymentId);
            return;
        }

        List<TransferItemResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        results.forEach(result -> {
            switch (result) {
                case TransferItemResult.Success s ->
                    bulkSettlementProcessor.assignTransferId(s.itemId(), s.transferId());
                case TransferItemResult.PendingInquiry p ->
                    bulkSettlementProcessor.markPendingInquiry(p.itemId(), p.messageNo());
                case TransferItemResult.Fail f ->
                    bulkSettlementProcessor.markFailed(f.itemId());
            }
        });
        boolean anyNotCompleted = results.stream()
                .anyMatch(r -> !(r instanceof TransferItemResult.Success));
        if (anyNotCompleted) {
            bulkSettlementProcessor.failedSettlement(portOnePaymentId);
            return;
        }
        bulkSettlementProcessor.completeSettlement(portOnePaymentId);
    }

    public void retrySettlement(String portOnePaymentId) {
        // PENDING_INQUIRY 아이템 먼저 재조회
        BulkSettlementContext inquiryContexts = bulkSettlementProcessor.loadPendingInquiryContexts(portOnePaymentId);
        List<CompletableFuture<TransferItemResult>> inquiryFutures = inquiryContexts.bulkSettlementItemContexts().stream()
                .map(context -> CompletableFuture.<TransferItemResult>supplyAsync(() -> {
                    WageTransferResult result = wageTransferPort.inquireTransfer(context.pendingMessageNo());
                    if (result.transferId() != null) {
                        return new TransferItemResult.Success(context.itemId(), result.transferId());
                    }
                    return new TransferItemResult.PendingInquiry(context.itemId(), result.pendingMessageNo());
                }, settlementExecutor
                ).exceptionally(e -> {
                    log.error("이체 결과 조회 실패 itemId={}", context.itemId(), e);
                    // TODO: 네트워크 예외 시 이중 송금 방지를 위해 전문번호 사전 생성 후 PendingInquiry 처리 필요
                    return new TransferItemResult.Fail(context.itemId());
                })).toList();
        try {
            CompletableFuture.allOf(inquiryFutures.toArray(new CompletableFuture[0]))
                    .orTimeout(30, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            log.error("이체 타임아웃", e);
            bulkSettlementProcessor.failedSettlement(portOnePaymentId);
            return;
        }
        List<TransferItemResult> results = inquiryFutures.stream()
                .map(CompletableFuture::join)
                .toList();
        results.forEach(result -> {
            switch (result) {
                case TransferItemResult.Success s ->
                    bulkSettlementProcessor.assignTransferId(s.itemId(), s.transferId());
                case TransferItemResult.PendingInquiry p ->
                    bulkSettlementProcessor.markPendingInquiry(p.itemId(), p.messageNo());
                case TransferItemResult.Fail f ->
                    bulkSettlementProcessor.markFailed(f.itemId());
            }
        });
        // PENDING/FAILED 아이템 재이체
        initiateBulkSettlement(portOnePaymentId);
    }

     public void failedPayment(String portOnePaymentId){
        bulkSettlementProcessor.failedPayment(portOnePaymentId);
     }
     public void receiveInterBankFailure(String transferId){
        bulkSettlementProcessor.receiveInterBankFailure(transferId);
     }
}
