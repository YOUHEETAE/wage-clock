# 통과하는데 틀린 테스트

이 프로젝트에서 찾은 결함 중 상당수는 **테스트가 있는 코드**에 있었다. 커버리지가 낮아서가
아니라, 테스트가 검증한다고 믿었던 것을 실제로는 검증하지 않고 있어서다.

세 가지 패턴이 반복됐다.

---

## 1. 목은 인자를 받아만 둔다

선지급 승인이 프로덕션에서 깨져 있었다. 어댑터에 넘어가는 `Worker`가 초기화되지 않은
프록시라, 계좌번호를 읽는 순간 `LazyInitializationException`이 났다.

통합 테스트는 전부 통과하고 있었다.

```java
@MockitoBean protected WageTransferPort wageTransferPort;
```

포트가 목으로 대체돼 있으니 `worker.getBankCode()`를 **아무도 부르지 않는다.** 목은 인자를
받아 기록하고 미리 정해둔 값을 돌려줄 뿐이다. 프록시는 끝까지 초기화되지 않고, 예외도
나지 않는다.

> **목은 호출됐는지를 검증하지, 넘긴 객체가 쓸 만한지는 검증하지 않는다.**

이걸 확인한 방법은 인자를 캡처해서 직접 만져보는 것이었다.

```java
ArgumentCaptor<Worker> captor = ArgumentCaptor.captor();
verify(wageTransferPort).transfer(captor.capture(), any(), any());
Worker worker = captor.getValue();
assertDoesNotThrow(worker::getAccountNumber);
```

이 테스트는 즉시 실패했다.

```
LazyInitializationException: Could not initialize proxy [Worker#18] - no session
```

### 대안 — 값을 읽는 대역

더 나은 방법은 목 대신 **실제 어댑터처럼 값을 읽는 페이크**를 두는 것이다.

```java
class FakeWageTransferPort implements WageTransferPort {
    public WageTransferResult transfer(TransferAccount account, BigDecimal amount, String messageNo) {
        Objects.requireNonNull(account.accountNumber());   // 여기서 터진다
        return new WageTransferResult(messageNo, null, null);
    }
}
```

목은 "이 메서드가 불렸는가"를 검증할 때 좋다. 하지만 **경계를 넘는 객체가 쓸 만한 상태인지**는
못 본다. 외부 포트를 태우는 통합 테스트만이라도 필드를 읽는 대역으로 두면 이 부류가 자동으로
걸린다.

결국은 포트가 엔티티 대신 값 객체를 받도록 고쳐서 이 문제를 구조적으로 없앴지만, 그건 다른
글의 주제다.

---

## 2. 셋업이 현실을 반영하지 않았다

계좌 미등록 검증을 추가했더니 **통합 테스트 36개가 한꺼번에 깨졌다.**

원인은 단순했다. 회원가입은 계좌 등록과 분리된 별도 API인데, 테스트 셋업이 계좌를 등록한 적이
없었다. 즉 **모든 통합 테스트가 계좌 없는 근로자에게 송금하고 있었다.**

목이 받아주니 통과했다. 실연동이었으면 전문에 빈 계좌번호가 실려 은행이 전부 거부했을 것이다.

```java
protected void signUp(String name, String email, UserRole role) {
    testRestTemplate.postForEntity("/api/auth/sign-up", ...);
    if (role == UserRole.WORKER) {
        registerAccountInfo(email, name);   // 계좌까지 등록된 상태로 만든다
    }
}
```

36개가 깨진 건 나쁜 소식이 아니었다. **테스트가 그동안 검증하던 세계가 실제와 달랐다는 증거**고,
그걸 고치자 비로소 현실에 가까워졌다.

> **셋업이 만드는 상태가 실제로 가능한 상태인지 의심한다.**

---

## 3. 불가능한 경우를 검증하고 있었다

일괄 정산에 이런 테스트가 있었다.

```java
@Test
void initiateBulkSettlement_워커없음_failItem_failSettlement() {
    ...
    when(workerRepository.findAllById(List.of(1L))).thenReturn(List.of());   // 워커를 못 찾음
    ...
    verify(bulkSettlementProcessor).failItem(10L);
}
```

통과한다. 그런데 이 상황은 **일어날 수 없다.** `employment.worker_id`가 `NOT NULL` 외래키라,
아이템과 PayPeriod가 존재하는데 Worker 행만 없는 상태를 DB가 허용하지 않는다.

반대로 **실제로 일어나는 경우**(계좌 미등록)는 아무도 검증하지 않고 있었다.

테스트는 통과했고 코드에는 방어 로직이 있었지만, 그 방어는 오지 않을 적을 막고 있었다.
검증 대상을 교체했다.

```java
@Test
void initiateBulkSettlement_계좌_미등록_failItem_failSettlement() {
    BulkSettlementItemContext context = new BulkSettlementItemContext(1L, BigDecimal.valueOf(50000),
            new TransferAccount(null, null, null), 10L, null);
    ...
}
```

> **가드가 막는 상황이 실제로 발생 가능한지 확인한다.** 발생할 수 없으면 그 가드도, 그 테스트도
> 아무것도 지키지 않는다.

---

## 4. 호출됐는지만 봤다

선지급액이 두 배로 계상되는 결함이 오래 살아남은 이유도 같은 계열이다. 기존 테스트가 이랬다.

```java
verify(payPeriod).addEwaAmount(BigDecimal.valueOf(100));
```

`addEwaAmount`가 불렸는지만 확인한다. 요청 시점과 성공 시점에 **두 번** 불려도 통과한다.
최종 누적값을 단정하지 않았기 때문이다.

고칠 때 두 가지를 바꿨다.

**최종값을 단정한다** — 계상 규칙 다섯 갈래(성공 / 확정실패 / UNKNOWN / 재시도성공 /
재시도소진)를 통합 테스트로 묶어 `totalEwaAmount`의 결과를 확인하게 했다.

**"하지 않는다"를 검증한다** — 이게 더 중요했다.

```java
// 요청 시점에 이미 더해졌으므로 성공 시에는 금액을 건드리지 않는다 — 조회조차 하지 않는다
verify(payPeriodRepository, never()).findByIdWithLock(any());
```

미확정 상태에서 한도를 되돌리지 않는다는 규칙도 마찬가지다.

```java
// 미확정(PENDING_INQUIRY·UNKNOWN)은 되돌리지 않는다 — 재이체되면 이중 송금이 된다
verify(payPeriod, never()).reopen();
```

**호출되지 않아야 하는 것을 검증하지 않으면, 중복 호출이나 불필요한 부수효과는 영원히 안
드러난다.** 특히 금액을 다루는 코드에서는 "한 번만 더한다"가 "더한다"보다 중요한 명제다.

---

## 실패를 재현할 수 있어야 실패 처리가 검증된다

여기까지가 잘못된 테스트 이야기라면, 반대로 **잘 만들어둔 것**도 하나 있었다.

이 프로젝트의 어려운 코드는 대부분 실패 경로에 있다. VTIM(처리중 응답), 타행이체불능 통지,
조회 실패, 프로세스 사망. 그런데 목 은행이 성공만 돌려주면 이 코드들은 **한 줄도 실행되지
않는다.**

그래서 목 펌뱅킹이 실패 시나리오를 재현할 수 있게 만들어져 있다. 타행이체불능 통지(3000 전문)는
실제로는 은행이 소켓으로 보내는 비동기 통지인데, 목에서는 HTTP 엔드포인트로 대체했다.

```java
@PostMapping("/3000")
public ResponseEntity<Void> receiveInterBankFailure(@RequestBody InterBankFailureNotification notification)
```

통합 테스트가 이걸 호출하면 아웃박스 재시도 흐름 전체가 실제로 돈다. 상태 전이, 재시도 카운트,
이중송금 방지 조기 종료까지.

> **재현할 수 없는 실패의 처리 코드는 검증되지 않은 코드다.**

---

## 정리

네 패턴은 결국 같은 질문 하나로 모인다.

**이 테스트가 실패할 수 있는가?**

- 목이 필드를 안 읽으면 → 프록시가 죽어 있어도 실패하지 않는다
- 셋업이 불가능한 상태를 만들면 → 현실에서 나는 오류가 재현되지 않는다
- 가드가 막는 상황이 발생 불가능하면 → 그 테스트는 영원히 통과한다
- 호출 여부만 보면 → 두 번 호출돼도 실패하지 않는다

커버리지는 **실행된 줄**을 세지, 그 줄이 틀렸을 때 빨간불이 켜지는지는 세지 않는다. 이번에
찾은 결함들은 전부 커버리지 안쪽에 있었다.

---

### 참고

- PR #65 `fix: correct EWA amount accounting and map confirmed transfer failure`
- PR #70 `refactor: make transfer port take account values instead of entities`
- 코드: `IntegrationTestBase.signUp`, `EwaTransferServiceTest`, `BulkSettlementServiceTest`,
  `MockFirmBankingSocketListener`
