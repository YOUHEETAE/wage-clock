# 비동기 블록 안에서 DB를 만지고 있었다

일괄 정산은 근로자 N명에게 동시에 송금한다. `CompletableFuture`로 병렬 처리하는데, 처음
짰을 때는 그 비동기 블록 안에 **외부 API 호출과 DB 쓰기가 같이 들어 있었다.**

동작은 했다. 테스트도 통과했다. 그런데 이 구조는 두 가지를 숨기고 있었다.

---

## 처음 코드

```java
List<CompletableFuture<TransferItemResult>> futures = contexts.bulkSettlementItemContexts().stream()
        .map(context -> CompletableFuture.<TransferItemResult>supplyAsync(() -> {
                    Worker worker = Optional.ofNullable(workerMap.get(context.workerId()))
                            .orElseThrow(() -> new NotFoundException("Worker not found"));
                    String messageNo = wageTransferPort.prepareTransfer(TransferType.BULK_SETTLEMENT);
                    bulkSettlementProcessor.assignMessageNo(context.itemId(), messageNo);   // ← DB 쓰기
                    WageTransferResult result = wageTransferPort.transfer(worker, context.amount(), messageNo);
                    if (result.transferId() != null) {
                        return new TransferItemResult.Success(context.itemId(), result.transferId());
                    } else if (result.pendingMessageNo() != null) {
                        return new TransferItemResult.PendingInquiry(context.itemId(), result.pendingMessageNo());
                    } else if (result.failureReason() != null) {
                        return new TransferItemResult.Fail(context.itemId(), result.failureReason());
                    }
                    return new TransferItemResult.Unknown(context.itemId());
                }, settlementExecutor)
                .exceptionally(e -> {
                    log.error("이체 결과 조회 실패 itemId={}", context.itemId(), e);
                    return new TransferItemResult.Unknown(context.itemId());
                })
        ).toList();

try {
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(30, TimeUnit.SECONDS)
            .join();
} catch (Exception e) {
    log.error("이체 타임아웃", e);
    bulkSettlementProcessor.failSettlement(portOnePaymentId);
    return;
}
```

람다 하나 안에 워커 조회, 전문번호 발급, **DB 쓰기**, 외부 호출, 결과 분류가 전부 있다.
그리고 같은 모양의 if/else 분류가 재조회 경로에도 복사돼 있었다.

---

## 문제 1 — 트랜잭션은 스레드를 넘지 않는다

`assignMessageNo`는 `@Transactional`이 붙은 메서드다. 그런데 이걸 워커 스레드에서 부르면
어떻게 될까?

Spring의 트랜잭션은 `ThreadLocal`에 묶여 있다. 호출한 쪽 스레드의 트랜잭션은 `supplyAsync`가
만든 다른 스레드로 **전파되지 않는다.** 즉 이 호출은 자기만의 트랜잭션을 새로 열고, 자기만의
커넥션을 잡는다.

그래서 이런 그림이 된다.

```
메인 스레드          트랜잭션 A ────────────────────────────────
워커 스레드 1                   트랜잭션 B ─┐
                                          │ 커넥션 점유
                                transfer() │ (최대 30초, 외부 호출)
                                          ─┘
워커 스레드 N                   트랜잭션 B+N ─ ...
```

동시에 이체하는 근로자가 많을수록 커넥션을 오래 붙잡은 채 외부 응답을 기다리게 된다.
커넥션 풀은 보통 10개 남짓이고, 이체 스레드 풀도 10개다. 사람이 늘면 마른다.

**그리고 실패했을 때 무엇이 롤백되는지가 불분명해진다.** 트랜잭션 경계가 코드에서 안 보이고,
스레드마다 따로 열리니 "어디까지 저장됐나"를 추론하기 어렵다.

## 문제 2 — 타임아웃을 배치 전체에 걸었다

`allOf(...).orTimeout(30초)`은 **전부 끝날 때까지 30초**를 잰다. 근로자 한 명의 응답이 늦으면
`join()`이 예외를 던지고, `catch`가 정산 전체를 실패로 떨어뜨린다.

이미 송금이 나간 나머지 사람들의 결과는 반영되지 않은 채로.

## 문제 3 — 분류를 틀리고 있었다

`Worker not found`를 `orElseThrow`로 던지면 `exceptionally`가 잡아서 `Unknown`으로 만든다.
그런데 `Unknown`은 **"돈이 나갔는지 모른다"**는 뜻이다. 조회 대상이 되고, 재시도 대상이 되고,
선지급 한도는 잡아둔 채 남는다.

워커를 못 찾은 건 송금이 시작조차 안 된 상태다. 재시도해도 결과가 같다. 이건 `Fail`이어야 한다.

**분류를 틀리면 그 돈의 운명이 바뀐다.**

---

## 고친 모양

```java
List<CompletableFuture<TransferItemResult>> futures = contexts.bulkSettlementItemContexts().stream()
        .map(context -> CompletableFuture.<TransferItemResult>supplyAsync(() ->
                        processItem(context, workerMap), settlementExecutor)
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(e -> handleTransferException(e, context.itemId()))).toList();

List<TransferItemResult> results = futures.stream()
        .map(CompletableFuture::join)
        .toList();

results.forEach(this::applyResult);
```

비동기 블록에는 **I/O만** 남기고, 상태 변경은 전부 메인 스레드로 나왔다. 메서드 셋으로 갈랐다.

| 메서드 | 책임 | 실행 스레드 |
|---|---|---|
| `processItem` | 외부 호출 + 결과를 값으로 반환 | 워커 |
| `toTransferItemResult` | 어댑터 응답 → 도메인 결과 분류 | 워커 |
| `handleTransferException` | 예외 → `Unknown` | 워커 |
| `applyResult` | 결과에 따른 DB 상태 변경 | **메인** |

`applyResult`가 상태 변경을 독점하니, **DB를 만지는 코드가 한 스레드에 모인다.** 트랜잭션
경계도 그 안에서만 논하면 된다.

> **비동기 블록에는 I/O만 둔다. 상태 변경과 DB 작업은 메인 스레드로 모은다.**
>
> 병렬화로 얻는 것은 외부 호출 대기 시간의 단축뿐이다. DB 작업까지 워커 스레드로 보내면
> 얻는 것 없이 트랜잭션 경계만 흩어진다.

타임아웃은 **future마다** 걸었다. 한 명이 늦으면 그 사람만 `Unknown`이 되고 나머지는 정상
처리된다. `Worker not found`는 `Fail`로 바꿨다.

---

## 이미 있던 도구를 제대로 쓰게 됐다

결과 타입은 처음부터 sealed interface였다.

```java
public sealed interface TransferItemResult permits Success, PendingInquiry, Fail, Unknown, Retryable {
    record Success(Long itemId, String transferId) implements TransferItemResult {}
    record PendingInquiry(Long itemId, String messageNo) implements TransferItemResult {}
    record Fail(Long itemId, String failureReason) implements TransferItemResult {}
    record Unknown(Long itemId) implements TransferItemResult {}
    record Retryable(Long itemId) implements TransferItemResult {}
}
```

`sealed`는 구현체를 다섯 개로 못박는다. 그래서 `applyResult`의 `switch`가 하나라도 빠지면
**컴파일이 안 된다.** 이체 결과 분기를 빠뜨리면 돈이 어긋나는 도메인이라, 이 강제력이 실제로
값을 한다.

이번에 다섯 번째로 `Retryable`을 추가했다. 전문번호 **발급 자체가 실패한 경우**다.

```java
case TransferItemResult.Retryable r -> {}   // 상태를 바꾸지 않는다
```

아무것도 하지 않는 것이 하나의 결과다. 은행에 요청이 나가지 않았으므로 아이템을 `PENDING`으로
둔 채 다음 사이클에 맡긴다. `Unknown`으로 두면 "번호가 나갔을지 모른다"는 뜻이 되어 조회
경로를 타는데, 발급이 안 됐으니 **조회할 번호 자체가 없다.**

`Retryable`을 별도 케이스로 만들지 않았다면 이 구분을 표현할 방법이 없었다.

---

## 그러면 상태 변경은 어디서 트랜잭션을 얻나

메인 스레드로 뺀 다음 문제가 생긴다. `applyResult`가 부르는 `completeItem` 같은 메서드에
`@Transactional`을 붙였는데, **같은 클래스 안에서 부르면 동작하지 않는다.**

Spring은 프록시로 트랜잭션을 건다. 외부에서 빈을 호출하면 프록시를 거치지만, 자기 자신의
메서드를 부르면 프록시를 우회해서 애너테이션이 무시된다.

그래서 상태 변경 메서드들을 별도 빈으로 뽑았다.

```java
// Spring 프록시 self-invocation 우회용 분리 클래스
@Component
public class BulkSettlementProcessor {

    @Transactional
    public void completeItem(Long itemId) { ... }
```

결과적으로 역할이 이렇게 갈렸다.

- `BulkSettlementService` — 흐름 조율, 외부 I/O, 스레드 관리
- `BulkSettlementProcessor` — 트랜잭션 경계, 상태 변경

self-invocation을 피하려고 시작한 분리인데, **의도치 않게 경계가 선명해졌다.** 지금은 "DB를
만지는 코드는 Processor에 있다"가 규칙처럼 굴러간다.

---

## 남은 것

배치를 정리하고 나면 남는 질문이 하나 더 있다. 그 경계로 **무엇을** 넘길 것인가.

> **스레드 경계를 넘는 것은 값이어야 한다.**

`BulkSettlementItemContext`라는 레코드가 그래서 있다. 엔티티를 워커 스레드로 넘기면 영속성
컨텍스트가 없는 곳에서 지연 로딩이 일어나기 때문이다.

그런데 이 원칙은 스레드 경계에만 해당하는 게 아니었다. **트랜잭션 경계에도 똑같이 적용되고,
그걸 지키지 않은 다른 경로가 나중에 실제로 깨졌다.**

다음 글에서 다룬다.

---

### 참고

- PR #24 `refactor: split BulkSettlement transfer logic and fix related concurrency/classification bugs`
- 코드: `BulkSettlementService`, `BulkSettlementProcessor`, `TransferItemResult`
