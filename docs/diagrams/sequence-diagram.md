# 시퀀스 다이어그램

EWA(선지급)와 일괄 정산(Bulk Settlement)은 같은 구조(메시지노우 사전생성 + RETRYING/UNKNOWN)를 공유한다.
EWA는 단건/동기 처리, Bulk는 N건/병렬 처리라는 차이만 있다.

---

## 1. EWA — 최초 이체 시도 (initiateEwa)

```mermaid
sequenceDiagram
    participant Employer
    participant EwaRequestService
    participant EwaTransferService
    participant WageTransferPort
    participant EwaTransferProcessor
    participant DB

    Employer->>EwaRequestService: initiateEwa(ewaRequestId)
    EwaRequestService->>EwaTransferService: processTransfer(ewaRequest)
    EwaTransferService->>EwaTransferProcessor: createEwaTransfer(ewaRequest)
    EwaTransferProcessor->>DB: save (status=PENDING)

    EwaTransferService->>WageTransferPort: prepareTransfer(EWA)
    WageTransferPort-->>EwaTransferService: messageNo
    EwaTransferService->>EwaTransferProcessor: assignMessageNo(id, messageNo)
    EwaTransferProcessor->>DB: 별도 트랜잭션 커밋 (transfer() 실패해도 messageNo는 보존됨)

    EwaTransferService->>WageTransferPort: transfer(worker, amount, messageNo)

    alt 성공 (transferId)
        WageTransferPort-->>EwaTransferService: transferId
        EwaTransferService->>EwaTransferProcessor: completed(id)
        EwaTransferProcessor->>DB: EwaTransfer COMPLETED / EwaRequest APPROVED / PayPeriod.addEwaAmount
    else 처리중 (VTIM)
        WageTransferPort-->>EwaTransferService: pendingMessageNo
        EwaTransferService->>EwaTransferProcessor: markPendingInquiry(id)
        EwaTransferProcessor->>DB: EwaTransfer PENDING_INQUIRY (EwaRequest는 PENDING 유지)
    else 확정 실패
        WageTransferPort-->>EwaTransferService: failureReason
        EwaTransferService->>EwaTransferProcessor: failed(id)
        EwaTransferProcessor->>DB: EwaTransfer FAILED / EwaRequest FAILED
    else messageNo 발급 실패 (prepareTransfer/assignMessageNo 예외)
        WageTransferPort--xEwaTransferService: Exception
        EwaTransferService->>EwaTransferProcessor: failTransfer(id)
        EwaTransferProcessor->>DB: EwaTransfer FAILED / EwaRequest FAILED
    else transfer() 예외 (네트워크 오류 등, messageNo는 이미 저장됨)
        WageTransferPort--xEwaTransferService: Exception
        EwaTransferService->>EwaTransferProcessor: unknownTransfer(id)
        EwaTransferProcessor->>DB: EwaTransfer UNKNOWN / EwaRequest UNKNOWN
    end

    EwaTransferService-->>EwaRequestService: EwaRequestStatus (리턴값, 재조회 없이 전달)
    EwaRequestService-->>Employer: InitiateEwaResponse
```

> `prepareTransfer()`로 받은 messageNo를 `transfer()` 호출 **전에** 별도 트랜잭션으로 먼저 커밋해두는 게 핵심이다.
> `transfer()`가 예외를 던져도 messageNo는 이미 저장돼 있어 UNKNOWN 상태도 나중에 재조회로 복구할 수 있다.

---

## 2. EWA — 전역 스케줄러 (PENDING_INQUIRY / UNKNOWN 재조회)

`EwaTransferFailureOutBoxEvent`가 없는 건(=첫 시도에서 바로 VTIM/예외가 난 건)을 책임지는 유일한 경로.

```mermaid
sequenceDiagram
    participant Scheduler as EwaTransferScheduler (5분 주기)
    participant EwaTransferService
    participant WageTransferPort
    participant EwaTransferProcessor

    loop 5분마다
        Scheduler->>Scheduler: findByStatusIn(PENDING_INQUIRY, UNKNOWN)
        loop 대상 EwaTransfer마다
            Scheduler->>EwaTransferService: inquiryTransfer(ewaTransfer)
            EwaTransferService->>WageTransferPort: inquireTransfer(기존 messageNo)
            alt 성공
                WageTransferPort-->>EwaTransferService: transferId
                EwaTransferService->>EwaTransferProcessor: completed(id)
            else 아직 처리중
                EwaTransferService->>EwaTransferProcessor: markPendingInquiry(id)
            else 확정 실패 / 예외
                EwaTransferService->>EwaTransferProcessor: failed(id) / unknown(id)
            end
        end
    end
```

---

## 3. EWA — 타행이체불능 수신 → Outbox 재시도

```mermaid
sequenceDiagram
    participant Bank as 핵토파이낸셜
    participant Listener as MockFirmBankingSocketListener
    participant EwaTransferService
    participant EwaTransferProcessor
    participant OutboxScheduler as OutBoxScheduler (30초 주기)
    participant OutboxService as EwaTransferFailureOutBoxService
    participant WageTransferPort

    Bank->>Listener: POST /mock/firm-banking/3000 (originalMessageNo)
    Listener->>Listener: TransferType.fromTransferId(messageNo) → EWA
    Listener->>EwaTransferService: receiveInterBankFailure(messageNo)
    EwaTransferService->>EwaTransferProcessor: receiveInterBankFailure(messageNo)
    EwaTransferProcessor->>EwaTransferProcessor: findByMessageNo → retrying()
    EwaTransferProcessor->>EwaTransferProcessor: ewaRequest.refundEwa(amount) (EwaRequest는 APPROVED 유지)
    EwaTransferProcessor->>EwaTransferProcessor: EwaTransferFailureOutBoxEvent(PENDING) 생성

    loop 30초마다
        OutboxScheduler->>OutboxService: processEvent(event)
        alt EwaTransfer가 이미 COMPLETED/FAILED (EwaTransferScheduler 등이 먼저 처리)
            OutboxService->>OutboxService: event.processed() → return (이중송금 방지)
        else EwaTransfer 상태가 PENDING_INQUIRY/UNKNOWN (이미 한 번 시도함)
            OutboxService->>WageTransferPort: inquireTransfer(기존 messageNo)
        else RETRYING (이번이 첫 재시도)
            OutboxService->>WageTransferPort: prepareTransfer(EWA) → 새 messageNo
            OutboxService->>WageTransferPort: transfer(worker, amount, 새 messageNo)
        end
        alt 성공
            OutboxService->>OutboxService: saveSuccess → completeRetry(COMPLETED, addEwaAmount) / event.processed()
        else 처리중 (PENDING_INQUIRY)
            OutboxService->>OutboxService: markPendingInquiry / event.processed()
            Note over OutboxService: Outbox는 종료 — EwaTransferScheduler가 이후 재조회 담당
        else 확정 실패
            OutboxService->>OutboxService: failRetry / event.failed()
        else 예외 · 애매한 응답
            OutboxService->>OutboxService: unKnownRetry / event.incrementRetryCount()
            Note over OutboxService: MAX_RETRY 도달 시 failRetry + event.failed() (운영팀 개입, 자동 재전송 금지 — 이중지급 위험)
        end
    end
```

---

## 4. Bulk — 일괄 정산 최초 시도 (병렬 처리)

```mermaid
sequenceDiagram
    participant PortOne
    participant Controller as PortOneWebhookController
    participant BulkSettlementService
    participant Executor as settlementExecutor (10개 스레드)
    participant WageTransferPort
    participant BulkSettlementProcessor

    PortOne->>Controller: webhook (Transaction.Paid)
    Controller->>BulkSettlementService: initiateBulkSettlement(portOnePaymentId)
    BulkSettlementService->>BulkSettlementProcessor: loadItemContexts(portOnePaymentId)

    par 아이템 N개 동시 처리
        BulkSettlementService->>Executor: supplyAsync(item 1)
        Executor->>WageTransferPort: prepareTransfer(BULK_SETTLEMENT)
        Executor->>BulkSettlementProcessor: assignMessageNo(item1Id, messageNo)
        Executor->>WageTransferPort: transfer(worker1, amount1, messageNo)
    and
        BulkSettlementService->>Executor: supplyAsync(item N)
        Executor->>WageTransferPort: prepareTransfer(BULK_SETTLEMENT)
        Executor->>BulkSettlementProcessor: assignMessageNo(itemNId, messageNo)
        Executor->>WageTransferPort: transfer(workerN, amountN, messageNo)
    end

    loop 결과별 분기 (각 future.join())
        BulkSettlementService->>BulkSettlementProcessor: completeItem / markPendingInquiry / failItem / unknownItem
        Note over BulkSettlementService: 워커 없음 → Fail / prepareTransfer 실패 → Retryable(no-op) / transfer() 예외·타임아웃 → Unknown
    end

    alt 전원 성공
        BulkSettlementService->>BulkSettlementProcessor: completeSettlement → BulkSettlement COMPLETED
    else 일부 미완료
        BulkSettlementService->>BulkSettlementProcessor: transferFailSettlement → BulkSettlement TRANSFER_FAILED
    end
```

> `BulkSettlementScheduler.retryFailedTransfers`(5분 주기)가 `TRANSFER_FAILED` 세틀먼트를 다시 집어서
> `retrySettlement`(PENDING_INQUIRY/UNKNOWN 재조회 → 남은 PENDING/FAILED 재시도)를 호출한다.
> Bulk는 EWA처럼 항목 전체를 긁는 전역 스케줄러가 없는 대신, 이 세틀먼트 상태를 게이트로 같은 역할을 한다.

---

## 5. Bulk — 타행이체불능 수신 → Outbox 재시도

```mermaid
sequenceDiagram
    participant Bank as 핵토파이낸셜
    participant Listener as MockFirmBankingSocketListener
    participant BulkSettlementService
    participant BulkSettlementProcessor
    participant OutboxScheduler as OutBoxScheduler (30초 주기)
    participant OutboxService as InterBankFailureOutBoxEventService
    participant WageTransferPort

    Bank->>Listener: POST /mock/firm-banking/3000 (originalMessageNo)
    Listener->>Listener: TransferType.fromTransferId(messageNo) → BULK_SETTLEMENT
    Listener->>BulkSettlementService: receiveInterBankFailure(messageNo)
    BulkSettlementService->>BulkSettlementProcessor: receiveInterBankFailure(messageNo)
    BulkSettlementProcessor->>BulkSettlementProcessor: findByMessageNo → item.retrying()
    BulkSettlementProcessor->>BulkSettlementProcessor: settlement.getStatus()==COMPLETED 일 때만 retrying() (TRANSFER_FAILED는 그대로 유지)
    BulkSettlementProcessor->>BulkSettlementProcessor: InterBankFailureOutBoxEvent(PENDING, bulkSettlementItemId) 생성

    loop 30초마다
        OutboxScheduler->>OutboxService: processEvent(event)
        OutboxService->>OutboxService: findByIdWithEmployment(bulkSettlementItemId)
        alt item 상태가 PENDING_INQUIRY/UNKNOWN
            OutboxService->>WageTransferPort: inquireTransfer(기존 messageNo)
        else RETRYING (이번이 첫 재시도)
            OutboxService->>WageTransferPort: prepareTransfer(BULK_SETTLEMENT) → 새 messageNo
            OutboxService->>WageTransferPort: transfer(worker, amount, 새 messageNo)
        end
        alt 성공
            OutboxService->>OutboxService: saveSuccess → completeRetry(item COMPLETED) → completeSettlement 재확인
            Note over OutboxService: 세틀먼트의 다른 아이템도 전부 COMPLETED면 BulkSettlement도 COMPLETED로 되돌림
        else 처리중
            OutboxService->>OutboxService: markPendingInquiry
        else 확정 실패
            OutboxService->>OutboxService: failItem / event.failed()
        else 예외 · 애매한 응답
            OutboxService->>OutboxService: unknownItem / event.incrementRetryCount()
            Note over OutboxService: MAX_RETRY 도달 시 failItem + event.failed() (운영팀 개입)
        end
    end
```