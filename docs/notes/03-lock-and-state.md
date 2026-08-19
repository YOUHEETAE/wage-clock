# 락으로 덮을 수 없는 구간

동시성 문제를 네 종류 만났다. 전부 "락을 걸면 되지 않나"로 시작했는데, 실제로 락이 답이었던
건 하나뿐이었다.

나머지는 **잠글 행이 없거나, 잠글 시간이 너무 길거나, 애초에 잠글 필요가 없는** 문제였다.

---

## 1. 같은 행을 고치는데 락이 없었다

`PayPeriod`에는 두 개의 금액 누계가 있다. 근무로 쌓이는 `totalEarnedAmount`와 선지급으로
쌓이는 `totalEwaAmount`다. 이 값을 고치는 경로가 넷인데, 락을 잡는 건 하나뿐이었다.

| 경로 | 동작 | 락 |
|---|---|---|
| EWA 요청 | `addEwaAmount` | 있음 |
| EWA 거절 | `subtractEwaAmount` | 없음 |
| 이체 확정 실패 | `subtractEwaAmount` | 없음 |
| 퇴근 | `addEarnedAmount` | 없음 |

넷 다 읽고-고쳐-쓰기다.

```
T1 EWA 거절                      T2 퇴근
  ewa=1000, earned=5000 읽음
                                 ewa=1000, earned=5000 읽음
  ewa 1000→0
  커밋 (ewa=0, earned=5000)
                                 earned 5000→7000
                                 커밋 (ewa=1000, earned=7000)  ← 거절이 사라짐
```

**주목할 건 T2가 `earned`만 고쳤다는 점이다.** 그런데도 `ewa`가 되살아났다. Hibernate가
변경된 컬럼만이 아니라 **전체 컬럼을 UPDATE**하기 때문이다. 서로 다른 컬럼을 건드리는 두
트랜잭션끼리도 덮어쓴다.

`totalEwaAmount`는 선지급 한도(`적립액 × 30% − 선지급액`)의 기준이다. 틀어지면 근로자가 한도를
넘겨 받을 수 있다.

이건 락이 맞는 경우다. 불변식이 `pay_periods` **한 행 안에서 닫히기** 때문이다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM PayPeriod p WHERE p.id = :id")
Optional<PayPeriod> findByIdWithLock(@Param("id") Long id);
```

네 경로가 전부 이 조회를 거치게 했다. 연관관계로 꺼내면 락이 안 걸리므로, 그것도 주석으로
못박았다.

---

## 2. 잠글 행이 없었다

출근 중복 방지는 원래 이렇게 되어 있었다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
boolean existsByEmploymentIdAndStatus(...)
```

`exists` 조회에 비관적 락을 걸었다. 그런데 **락은 존재하는 행에만 걸린다.** 세션이 없는
상태에서 두 요청이 동시에 오면 둘 다 "없음"을 보고 둘 다 INSERT한다. PostgreSQL은 갭 락을
하지 않으므로 막히지 않는다.

같은 함정이 더 큰 규모로 한 번 더 나왔다. 정산 시작 시 "근무 중인 사람이 없어야 한다"를
검사하는데, 출근과 정산이 **서로 다른 행을 잠그고 있었다.**

```
T1 clockIn                       T2 createBulkSettlement
  Employment 락 획득
  근무 세션 있나? → 없음             PayPeriod 락 획득
                                  활성 세션 있나? → 없음 (T1 미커밋)
  세션 INSERT
  커밋                             SETTLING 전이 · 커밋
→ 정산에 들어갔는데 근무 중인 사람이 있다
```

전형적인 write skew다. 서로의 미커밋 변경을 못 보니 양쪽 검사가 다 통과한다.

**"같은 행을 보는가"가 기준이 아니었다.** 두 흐름은 다른 행을 봤는데도 문제가 생겼다.
실제 기준은 **같은 불변식을 지키는가**이고, 그렇다면 **둘이 공통으로 잡을 수 있는 행**을
찾아야 한다.

여기서는 WorkSession도 PayPeriod도 "아직 없을 수 있는" 것이라 기준점이 못 된다. 두 흐름에
**반드시 존재하는** Employment를 골랐다.

```java
// 출근 중복 진입을 막는 락.
// WorkSession이나 PayPeriod는 아직 없을 수 있어 잠글 행이 없으므로,
// 반드시 존재하는 Employment를 기준점으로 삼는다.
```

> **없는 행은 잠글 수 없다. 조건이 "~가 없어야 한다"면 기준점을 따로 골라야 한다.**

---

## 3. 잠글 시간이 너무 길었다

정산 이체의 진입점은 셋이다 — 웹훅, 미수신 확인 스케줄러, 재이체 스케줄러. 그리고 PG는
응답이 늦으면 웹훅을 재전송한다. 같은 정산에 동시에 들어오는 일이 실제로 발생한다.

아이템 상태는 이체가 끝난 뒤에야 바뀌므로, "읽었지만 아직 안 바뀐" 구간이 **이체 전체 길이**
만큼 벌어진다. 아이템당 최대 30초, N명이면 그만큼 더.

이체 작업 전체를 락으로 감싸면 될까? 안 된다.

- 아이템별 이체가 `CompletableFuture`로 **별도 스레드**에서 돈다. 비관적 락은 그것을 잡은
  트랜잭션에 매여 있어 워커 스레드의 작업을 보호하지 못한다
- 외부 API 호출이 아이템당 30초다. 그동안 커넥션과 락을 붙잡고 있을 수 없다

그래서 **락으로 짧게 상태만 바꾸고, 긴 작업은 그 상태가 보호하게** 했다.

```java
@Transactional
public boolean claimForTransfer(String portOnePaymentId) {
    BulkSettlement settlement = bulkSettlementRepository
            .findByPortOnePaymentIdWithLock(portOnePaymentId)
            .orElseThrow(...);
    if (settlement.getStatus() != PROCESSING && settlement.getStatus() != TRANSFER_FAILED) {
        return false;
    }
    settlement.transferring();
    return true;
}
```

락은 **상태 확인과 전이 사이**만 보호한다. 그 사이에 다른 트랜잭션이 끼어들면 둘 다 통과하기
때문이다. 전이가 끝나면 락을 놓고, 이후 긴 이체는 `TRANSFERRING`이라는 상태가 막는다.

두 번째 진입자는 **대기하지 않고 즉시 물러난다.** 한 번만 일어나야 하는 작업이므로 기다릴
이유가 없다.

`TRANSFERRING`이 필요했던 근본 이유는 따로 있다. `PROCESSING`이 "입금 대기"와 "이체 중"을
겸하고 있어서, 미수신 확인 스케줄러가 진행 중인 정산을 다시 집었다. **상태 하나가 두 사실을
나타내던 것**이 원인이었다.

---

## 4. 트랜잭션보다 긴 구간

정산은 이 순서로 흐른다.

```
총액 확정 → 가상계좌 발급 → 사장이 입금 → 웹훅 → 이체 → 마감
                          ↑
                    사람이 은행 앱을 여는 시간
```

가운데에 **수 분에서 수 시간**이 들어 있다. 그런데 총액은 맨 앞에서 못 박힌다
(`BulkSettlementItem.amount`).

이 구간에 근로자가 출근했다가 퇴근하면 어떻게 될까?

```
T0  정산 요청       활성 세션 없음 확인 → 통과, 총액 확정
T1  근로자 출근     ACTIVE인 PayPeriod에 세션이 붙음   ← 막는 게 없다
    ...            (사장 입금 대기)
T2  입금 → 이체 → PayPeriod CLOSED
T3  근로자 퇴근     CLOSED된 PayPeriod에 적립액이 더해짐
```

T3에서 들어온 적립액은 이미 확정된 이체액에 반영되지 않는다. 그리고 다음 출근은 새 PayPeriod를
만들기 때문에, 정산 대상 조회(`status = 'ACTIVE'`)가 그 돈을 다시 잡지 못한다.

**지워지는 게 아니라 닿을 수 없는 곳에 남는다.**

락으로는 못 막는다. T0~T2를 잠글 수는 없기 때문이다. 그래서 상태로 막았다.

```
ACTIVE ──startSettling()──▶ SETTLING ──close()──▶ CLOSED
                               └──reopen()──▶ ACTIVE
```

`SETTLING` 동안은 출근이 거부된다. 정산이 무산되면 `ACTIVE`로 되돌린다.

> **락은 트랜잭션이 끝나면 풀린다. 그보다 긴 구간은 상태로 덮는다.**

### 되돌리는 것과 되돌리지 않는 것

`reopen`을 부르는 곳은 둘뿐이다.

| 상황 | 처리 |
|---|---|
| 이체 확정 실패 (`failItem`) | `reopen` — 돈이 안 나간 게 분명하다 |
| 사장 미입금·취소 (`failPayment`) | `reopen` — 돈이 아예 안 움직였다 |
| `PENDING_INQUIRY` · `UNKNOWN` | **되돌리지 않는다** |

미확정을 `ACTIVE`로 풀면 재정산 대상이 되고, 그건 이중 송금이다. 결과를 모르는 동안은
잠가둔 채로 조회가 확정해줄 때까지 기다린다.

### 왜 새 PayPeriod를 만들지 않았나

정산 중 출근을 허용하고 새 PayPeriod를 만드는 방법도 있었다. 하지만 employment당 진행 중인
PayPeriod는 하나여야 한다 — 조회들이 `Optional`을 반환하기 때문이다. 정산이 실패해
`SETTLING`을 `ACTIVE`로 되돌리는 순간 `ACTIVE`가 두 개가 된다.

그래서 출근 자체를 막았다. 대신 `SETTLING`을 조회에서 빼면 정산 거는 순간 화면이 비므로,
근로자 요약·사장 요약·EWA 내역을 `ACTIVE` 또는 `SETTLING`으로 넓혔다.

---

## 정리 — 무엇으로 막을지 고르는 기준

네 사례를 겪고 나서 판단 순서가 이렇게 정리됐다.

**1. 멱등하게 만들 수 있나** → 락이 필요 없다

유니크 제약이나 상태 전이 가드로 "두 번 실행돼도 결과가 같게" 만들 수 있으면 그게 제일
간단하다. 분산 환경에서도 그대로 성립한다. EWA 요청의 `idempotencyKey`가 이 방식인데,
검사 자체도 경합에 노출되므로 **실제 방어선은 DB의 유니크 제약**이다.

**2. 누계를 증감해야 하나** → 락 또는 원자적 UPDATE

`+1000`은 두 번 하면 `+2000`이라 본질적으로 멱등하지 않다. 1번 사례가 여기 해당한다.

**3. 불변식이 여러 행에 걸쳐 있나** → 공통 기준점을 잡아 락

2번 사례. "없는 행은 잠글 수 없다"가 여기서 나온다.

**4. 트랜잭션보다 긴 구간인가** → 상태

3번과 4번 사례. `TRANSFERRING`과 `SETTLING`이 각각 이 답이다.

그리고 락을 쓰기로 했다면 **순서를 고정한다.** 이 프로젝트는 PayPeriod를 항상 마지막에
잡는다 — `Employment → PayPeriod`, `EwaRequest → PayPeriod`, `BulkSettlement` 단독. 역방향
경로가 없으므로 사이클이 생기지 않는다. 여러 행을 한 번에 잠글 때는 `ORDER BY`로 획득 순서를
못박는다.

| 기준 행 | 잠그는 흐름 | 지키는 것 |
|---|---|---|
| Employment | `clockIn`, `createBulkSettlement` | 출근과 정산 진입의 직렬화 |
| PayPeriod | EWA 요청·거절, `clockOut`, 이체 실패 확정 | 금액 누계 |
| BulkSettlement | `claimForTransfer` | 이체 선점 |
| 상태 | `SETTLING`, `TRANSFERRING` | 트랜잭션보다 긴 구간 |

---

## 남은 것

`TRANSFERRING`으로 선점하고 나면 새 문제가 생긴다. **이체 도중 프로세스가 죽으면** 그 상태로
영원히 남는다. 선점된 상태라 어느 스케줄러도 훑지 않기 때문이다.

예외 처리로는 잡을 수 없다. 프로세스가 사라지면 `catch`도 `finally`도 실행되지 않는다.

그래서 시간이 지난 `TRANSFERRING`을 회수하는 스케줄러를 뒀는데, 회수할 때 **전문번호가 발급된
아이템은 `UNKNOWN`으로** 돌린다. 번호가 이미 은행에 나갔을 수 있어 재이체하면 이중 송금이 된다.

돈이 나갔는지 모르는 상태를 어떻게 다룰 것인가 — 다음 글의 주제다.

---

### 참고

- PR #64 `fix: enforce work session state invariants and serialize clock-in with period close`
- PR #66 `fix: verify webhook payments and prevent concurrent settlement transfer`
- PR #67 `feat: validate and lock pay period during bulk settlement`
- PR #68 `fix: lock pay period row when updating amount totals`
- 코드: `PayPeriod`, `BulkSettlementProcessor.claimForTransfer`, `EmploymentRepository`, `PayPeriodRepository`
