# 돈이 나갔는지 모를 때

송금에는 세 가지 결과가 있다고 생각하기 쉽다. 성공, 실패, 그리고 아직 진행 중.

실제로는 네 번째가 있다. **결과를 모르는 상태.**

이 상태를 어떻게 다루느냐가 이 프로젝트에서 가장 많은 코드를 만들었다.

---

## 왜 네 번째가 생기나

이체 요청을 보내고 응답을 못 받았다고 하자. 네트워크가 끊겼거나, 타임아웃이 났거나,
프로세스가 죽었다.

이때 은행에서 무슨 일이 일어났는지 **알 수 없다.** 요청이 도달하지 않았을 수도 있고,
도달해서 송금까지 됐는데 응답만 못 온 것일 수도 있다.

여기서 잘못 판단하면 둘 중 하나가 된다.

- **실패로 확정하고 재시도** → 이미 나간 돈을 또 보낸다
- **성공으로 확정** → 안 나간 돈을 나갔다고 기록한다

그래서 "모른다"를 **모른다는 상태 그대로** 저장해야 한다.

```java
public enum EwaTransferStatus {
    PENDING, PENDING_INQUIRY, COMPLETED, FAILED, UNKNOWN, RETRYING
}
```

`FAILED`와 `UNKNOWN`을 가르는 기준은 **돈이 나갔는지 알 수 있는가**다.

---

## 전문번호를 먼저 저장한다

모르는 상태를 나중에 확정하려면 **조회할 수 있어야** 한다. 펌뱅킹은 전문번호(messageNo)로
결과를 조회한다.

그래서 이 순서가 중요해진다.

```java
String messageNo = wageTransferPort.prepareTransfer(TransferType.EWA);
ewaTransferProcessor.assignMessageNo(ewaTransferId, messageNo);   // 별도 트랜잭션으로 먼저 커밋
WageTransferResult result = wageTransferPort.transfer(account, amount, messageNo);
```

전문번호를 **이체 호출 전에** 커밋해둔다. `transfer()`가 예외를 던져도 번호는 이미 저장돼
있으므로, 나중에 그 번호로 조회해 결과를 확정할 수 있다.

반대로 번호를 이체 후에 저장했다면, 예외가 난 건은 조회할 방법이 없어 영원히 미확정으로
남는다.

### 그래서 두 실패를 분리해야 한다

```java
String messageNo;
try {
    messageNo = issueTransferMessageNo(ewaTransferId);
} catch (Exception e) {
    // 발급 실패 — 은행에 요청이 나가지 않았다
    ewaTransferProcessor.failTransfer(ewaTransferId);
    return EwaRequest.EwaRequestStatus.FAILED;
}
try {
    WageTransferResult result = wageTransferPort.transfer(account, amount, messageNo);
    return applyTransferStatus(result, ewaTransferId);
} catch (Exception e) {
    // 이체 실패 — 번호가 이미 나갔을 수 있다
    ewaTransferProcessor.unknownTransfer(ewaTransferId);
    return EwaRequest.EwaRequestStatus.UNKNOWN;
}
```

두 경로를 한 `try-catch`로 묶으면, **발급이 실패한 건이 존재하지 않는 번호로 조회를 시도하게
된다.** 조회는 당연히 실패하고, 그 건은 아웃박스 재시도를 소진할 때까지 미확정으로 떠돈다.

> **전문번호가 나가기 전의 실패는 확정 실패, 나간 뒤의 실패는 미확정이다.**

같은 이유로 일괄 정산에는 `Retryable`이라는 결과가 따로 있다. 발급 자체가 실패했으므로
아이템 상태를 건드리지 않고 `PENDING`인 채 다음 사이클에 맡긴다.

---

## 한도는 요청할 때 잡는다

선지급은 적립액의 30%까지만 받을 수 있다. 이 한도를 언제 차감할 것인가?

처음 코드는 **요청할 때와 이체 성공할 때 둘 다** 더하고 있었다. 100원을 선지급받으면
`totalEwaAmount`가 200이 됐다. 근로자는 100원을 받고 월급에서 200원이 깎이며, 사장이 결제할
정산 총액까지 틀어졌다.

규칙을 하나로 정리했다.

| 지점 | 금액 |
|---|---|
| EWA 요청 | `+ amount` |
| 거절 | `− amount` |
| 이체 확정 실패 | `− amount` |
| 이체 성공 | — (요청 시점에 이미 반영됨) |
| 불능통지(`RETRYING`) · `UNKNOWN` | — (미확정이므로 계속 잡아둔다) |

**요청 시점에 잡는 이유**는 승인 대기와 이체 진행 중에도 한도가 소진된 것으로 보여야 하기
때문이다. 성공 시점에 더하면 결과를 모르는 구간에 한도가 비어, 근로자가 한도를 초과해 요청할
수 있다. 카드 가승인과 같은 이유다.

**미확정에서 환원하지 않는 이유**는 조금 다르다. 되돌리면 재시도가 성공했을 때 다시 더해야
하는데, 최초 성공 경로와 재시도 성공 경로가 `completeTransfer`를 공유한다. 그러면 어느 쪽으로
고쳐도 한쪽이 반드시 틀어진다.

> **확정된 실패만 되돌린다. 모르는 것은 잡아둔 채로 둔다.**

이 규칙은 `PayPeriod.addEwaAmount`에 주석으로 박아뒀다. 계상 지점이 다섯 곳으로 흩어져 있어서,
규칙이 코드 한 곳에 있어야 다음에 손댈 때 어긋나지 않는다.

---

## 도달할 수 없는 분기가 있었다

어댑터는 은행 응답을 도메인 결과로 번역한다.

```java
public ResultType classify(){
    if(transferId != null) return ResultType.SUCCESS;
    if(pendingMessageNo != null) return ResultType.PENDING_INQUIRY;
    if(failureReason != null) return ResultType.FAILURE;
    return ResultType.UNKNOWN;
}
```

그런데 **`failureReason`을 채우는 코드가 프로덕션에 하나도 없었다.** 어댑터가 정상(0000)이
아닌 모든 응답을 재조회로 보내고 있었기 때문이다.

결과적으로 `classify()`의 `FAILURE` 분기가 네 곳 모두 도달 불가능했다. 잔액 부족 같은 확정
실패도 `PENDING_INQUIRY` → 조회 실패 → `UNKNOWN` → 아웃박스 재시도 소진을 거친 뒤에야
`FAILED`가 됐다. 그동안 근로자에게는 "처리 중"으로 보이고 실패 사유는 유실됐다.

조회 응답을 네 갈래로 나눠 고쳤다. 그런데 **이체 응답은 고치지 않았다.**

응답코드 리스트가 은행별로 다르고 아직 확보하지 못했다. 정상 외의 코드 중에 "접수됐으니
나중에 조회하라"는 의미가 섞여 있으면, 실제로 나간 이체를 실패로 확정해버린다. 판단을 조회에
위임하는 지금 동작이 **안전한 쪽**이므로 그대로 두고 의도를 잘못 전달하던 주석만 정정했다.

> **모를 때는 안전한 쪽으로 틀린다.** 이 도메인에서 안전한 쪽은 "미확정"이다.

---

## 조회 실패와 이체 실패는 다르다

재조회 응답에는 두 층이 있다.

- **공통부 응답코드** — 조회 전문 자체가 처리됐는가
- **개별부 처리결과** — 원래 이체가 어떻게 됐는가

조회 전문이 실패하면 원 이체의 결과를 **알 수 없다.** 여기서 실패로 확정하면 돈이 나갔을 수도
있는 건의 한도를 되돌리게 된다. 그래서 예외를 던져 `UNKNOWN`으로 보낸다.

한 응답 안에서도 "무엇에 대한 실패인가"를 가려야 한다는 뜻이다.

---

## 죽으면 아무도 기록을 못 남긴다

이체 중 프로세스가 죽으면 정산은 `TRANSFERRING`인 채 남는다. 선점된 상태라 어느 스케줄러도
훑지 않는다.

아웃박스로 해결되지 않는다. 아웃박스는 **실패를 인지한 쪽이 기록을 남길 수 있을 때** 쓰는
패턴인데, 프로세스가 사라지면 `catch`도 `finally`도 실행되지 않는다.

그래서 시간이 지난 `TRANSFERRING`을 훑어 회수하는 스케줄러를 뒀다.

```java
settlement.getItems().stream()
        .filter(item -> item.getStatus() == PENDING)
        .filter(item -> item.getMessageNo() != null)
        .forEach(BulkSettlementItem::unknown);
settlement.transferFailed();
```

여기서도 같은 기준이 나온다. **전문번호가 발급된 `PENDING` 아이템은 `UNKNOWN`으로** 돌린다.
번호가 이미 은행에 나갔을 수 있어 재이체하면 이중 송금이 된다. 번호가 없는 아이템은 요청이
나가지 않았으므로 `PENDING`인 채 재이체 대상으로 남는다.

> **실패를 인지한 쪽이 기록을 남길 수 있으면 아웃박스, 아무도 남기지 못하고 사라지면 스위퍼.**

---

## 그래서 자동 재시도를 어디서 멈추나

`FAILED`는 종착점이다. 자동 재시도가 없다.

미확정 상태의 재시도는 유한하다. 아웃박스가 `MAX_RETRY`를 소진하면 확정 실패로 떨어뜨리고
운영팀 개입 대상이 된다. 무한히 재시도하지 않는 이유는, 계속 모르는 상태로 두는 것보다
사람이 은행 기록을 확인하는 편이 정확하기 때문이다.

한 가지 예외가 있다. **계좌 미등록**은 재시도가 의미 있다.

```java
// 계좌가 없으면 전문번호를 발급하지 않고 재시도 카운트만 태운다.
// 근로자가 그 사이 계좌를 등록하면 다음 재시도에서 성공하므로 즉시 확정 실패시키지 않는다.
```

같은 "지금은 못 보낸다"라도, **시간이 지나면 조건이 바뀌는 실패**와 그렇지 않은 실패는
다르게 다뤄야 한다.

---

## 남은 것

이 글의 규칙들은 전부 테스트로 고정했다. 계상 규칙 다섯 갈래(성공 / 확정실패 / UNKNOWN /
재시도성공 / 재시도소진)를 통합 테스트로 묶고, 기존 단위 테스트의 `verify`를 `never()`로
뒤집어 **"금액을 건드리지 않는다"**를 검증하게 했다.

그런데 이 결함들이 오래 살아남은 이유가 바로 테스트였다. 기존 테스트는
`verify(payPeriod).addEwaAmount(...)`처럼 **"호출됐는가"만 확인하고 최종 누적값을 단정하지
않았다.** 두 번 호출되는 것도 통과했다.

테스트가 통과하는데 프로덕션이 틀리는 경우 — 다음 글의 주제다.

---

### 참고

- PR #65 `fix: correct EWA amount accounting and map confirmed transfer failure`
- PR #66 `fix: verify webhook payments and prevent concurrent settlement transfer`
- 코드: `PayPeriod.addEwaAmount`(계상 규칙 주석), `WageTransferResult.classify`,
  `EwaTransferService.issueTransferMessageNo`, `BulkSettlementProcessor.recoverStaleTransfer`
