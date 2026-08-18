# 포트가 엔티티를 요구하고 있었다

지난 글에서 스레드 경계를 넘는 것은 값이어야 한다고 정리했다. 그런데 같은 문제가
**트랜잭션 경계에도 있었고**, 거기서는 규칙을 지키지 못한 경로가 하나 있었다.

그 경로는 프로덕션에서 깨져 있었다. 테스트는 전부 통과하고 있었다.

---

## 증상

선지급(EWA)을 사장이 승인하면 근로자에게 바로 송금한다. 그 경로가 이랬다.

```java
public InitiateEwaResponse initiateEwa(Long ewaRequestId, Long employerId){   // @Transactional 없음
    EwaRequest ewaRequest = ewaRequestProcessor.validateAndMarkProcessing(ewaRequestId, employerId);
    EwaRequest.EwaRequestStatus status = ewaTransferService.processTransfer(ewaRequest);
    ...
}
```

`validateAndMarkProcessing`이 끝나면 트랜잭션이 닫힌다. 그 뒤로 `ewaRequest`는 **준영속**
상태다. 그리고 이체 직전에 이런 코드가 있었다.

```java
wageTransferPort.transfer(ewaTransfer.getWorker(), ewaTransfer.getAmount(), messageNo);
```

`getWorker()`는 겉보기엔 필드 접근이지만 실제로는 `payPeriod → employment → worker`를 타는
연관관계 조회다. 영속성 컨텍스트가 닫힌 뒤에 이걸 부르면 프록시를 채울 세션이 없다.

어댑터가 계좌번호를 읽는 순간 `LazyInitializationException`이 난다.

```
org.hibernate.LazyInitializationException:
Could not initialize proxy [Worker#18] - no session
```

### 이 예외가 난 것 자체가 다행이었다

이 프로젝트는 `open-in-view`를 꺼두고 있다.

```properties
spring.jpa.open-in-view=false
```

켜져 있으면 영속성 컨텍스트가 **HTTP 요청이 끝날 때까지** 열려 있다. 그러면 트랜잭션이
닫혀도 프록시가 초기화되므로, 위 코드는 웹 요청에서 아무 문제 없이 동작한다.

문제는 그게 **요청 스코프에서만** 성립한다는 점이다. 스케줄러나 아웃박스처럼 HTTP 요청이
아닌 경로에는 그 안전망이 없다. 결국 같은 코드가 화면에서는 되고 배치에서만 터진다 —
재현도 어렵고 원인도 헷갈리는 형태로.

OSIV를 끄면 트랜잭션 경계와 컨텍스트 수명이 일치한다. 경계를 넘는 코드는 **어디서 부르든
똑같이 실패하므로**, 문제가 개발 중에 드러난다.

> **안전망이 일부 경로에만 있으면, 없는 것보다 찾기 어려워진다.**

---

## 왜 테스트가 통과했나

통합 테스트는 `WageTransferPort`를 목으로 대체한다.

```java
@MockitoBean protected WageTransferPort wageTransferPort;
```

목은 인자를 **받아만 둔다.** `worker.getBankCode()`를 아무도 부르지 않으니 프록시는 끝까지
초기화되지 않고, 예외도 나지 않는다.

> **목은 호출됐는지를 검증하지, 넘긴 객체가 쓸 만한지는 검증하지 않는다.**

이 문제를 실제로 드러내려면, 목 대신 **필드를 읽는 대역**이 필요했다. 그래서 나중에
회귀 테스트를 이렇게 넣었다.

```java
ArgumentCaptor<Worker> captor = ArgumentCaptor.captor();
verify(wageTransferPort).transfer(captor.capture(), any(), any());
assertDoesNotThrow(captor.getValue()::getAccountNumber);
```

---

## 원인은 한 층 아래에 있었다

처음에는 `initiateEwa`에 `@Transactional`을 붙이거나, 미리 `JOIN FETCH`로 채워두는 걸
생각했다. 둘 다 이 자리는 고치지만 원인은 그대로다.

원인은 **포트의 시그니처**였다.

```java
WageTransferResult transfer(Worker worker, BigDecimal amount, String messageNo);
```

포트가 JPA 엔티티를 요구한다. 그런데 이체는 최대 수십 초짜리 외부 호출이라 트랜잭션 안에
둘 수 없다. **트랜잭션을 붙일 수 없는 자리에서 살아 있는 엔티티를 마련해야 하는** 상황이
되고, 호출부마다 각자 방법을 찾게 된다.

| 경로 | 마련 방식 |
|---|---|
| 일괄 정산 | `findAllById`로 Worker를 미리 로딩해 Map으로 전달 |
| 아웃박스 재이체 | `findByIdWithWorker` (JOIN FETCH) |
| 타행이체불능 재처리 | `findByIdWithEmployment` (JOIN FETCH) |
| **EWA 승인** | **없음 — 연관관계 체이닝** |

세 곳이 서로 다른 우회로를 만들었고, 한 곳은 우회에 실패했다. 개발자가 일관성이 없어서가
아니라 **포트가 요구하는 걸 각 상황에서 어떻게든 마련해야 했기 때문**이다.

지연 로딩은 숨겨진 I/O다. `getWorker()`는 필드 접근처럼 생겼지만 SELECT다. 그리고
트랜잭션을 못 붙이는 자리는 정의상 I/O를 할 수 없는 자리다. 거기서 연관관계를 타려는
시도 자체가 범주 오류였다.

---

## 고친 모양

포트가 값 객체를 받게 했다.

```java
public record TransferAccount(String bankCode, String accountNumber, String holderName) {
    public boolean isRegistered() {
        return bankCode != null && accountNumber != null && holderName != null;
    }
}

WageTransferResult transfer(TransferAccount account, BigDecimal amount, String messageNo);
```

매핑은 엔티티가 맡는다.

```java
// Worker
public TransferAccount toTransferAccount() {
    return new TransferAccount(bankCode, accountNumber, accountHolder);
}
```

> **엔티티는 트랜잭션 경계를 넘지 않는다. 넘길 것은 값으로 만든다.**
>
> 값으로 받으면 뽑는 일이 반드시 트랜잭션 안에서 끝나야 하므로, 규율로 지키던 것이
> 타입으로 강제된다.

EWA 승인은 엔티티 대신 ID를 받고, 자기 트랜잭션 안에서 다시 조회해 계좌를 확정한다.

```java
@Transactional
public EwaTransferContext createEwaTransfer(Long ewaRequestId){
    EwaRequest ewaRequest = ewaRequestRepository.findById(ewaRequestId)
            .orElseThrow(() -> new NotFoundException("EwaRequest Not Found"));
    ...
    return new EwaTransferContext(
            ewaTransfer.getId(),
            ewaTransfer.getAmount(),
            ewaRequest.getWorker().toTransferAccount());   // 트랜잭션 안이라 안전
}
```

정산 쪽의 `workerMap`은 사라졌다. 계좌가 컨텍스트에 담기니 Worker를 미리 로딩해 들고 다닐
이유가 없어졌고, `WorkerRepository` 의존도 함께 빠졌다.

**인프라 계층에서 도메인 엔티티 의존이 0이 됐다.**

```bash
grep -rn "domain.worker" src/main/java/.../infrastructure/   # 결과 없음
```

### 그렇다고 항상 ID를 넘기라는 뜻은 아니다

규칙은 "엔티티를 넘기지 마라"가 아니라 **"경계를 넘길 때 넘기지 마라"**다.

같은 트랜잭션 안이라면 엔티티를 그대로 넘기는 편이 낫다. ID로 넘기면 받는 쪽이 다시
조회해야 하고, 그건 공짜가 아니다.

- 같은 트랜잭션이면 1차 캐시가 받아주지만, 다른 트랜잭션이면 실제 쿼리가 한 번 더 나간다
- 무엇보다 **재조회하는 사이에 상태가 바뀌었을 수 있다.** 넘긴 시점과 읽는 시점이 달라지므로,
  그 간격이 문제가 되는 작업이면 락이나 상태 검증이 추가로 필요해진다

`createEwaTransfer`가 ID를 받는 건 **트랜잭션 경계를 넘기 때문**이지, ID가 엔티티보다 좋아서가
아니다. 경계 안에서 나뉜 메서드끼리는 엔티티를 주고받아도 된다.

> 판단 기준은 "무엇을 넘기나"가 아니라 **"영속성 컨텍스트가 그 사이에 닫히나"**다.

---

## 중간에 한 번 더 틀렸다

ID를 받도록 바꾸는 것만으로는 부족했다. 처음 고쳤을 때 이랬다.

```java
@Transactional
public EwaTransfer createEwaTransfer(Long ewaRequestId){   // 여전히 엔티티를 반환
    ...
    return ewaTransfer;
}
```

ID를 받아 트랜잭션 안에서 조회했지만 **엔티티를 그대로 내보냈다.** 밖에서
`ewaTransfer.getWorker()`를 부르면 결과는 같다. 오히려 더 일찍 터진다 — 새 트랜잭션에서
갓 조회한 인스턴스라 `payPeriod`부터 미초기화 상태이기 때문이다.

경계를 넘는 **타입**이 바뀌어야 해결된다. 받는 쪽을 ID로 바꾼 건 절반이었다.

---

## 1차 캐시라는 함정

락을 거는 작업에서도 같은 성격의 문제를 만났다. 금액 누계를 고치기 전에 PayPeriod를 락으로
다시 조회하는데, **조회 순서가 중요했다.**

```java
// 잘못된 순서
validateEwa(ewaRequest, employerId);   // getEmployerId()가 payPeriod 프록시를 초기화
PayPeriod payPeriod = payPeriodRepository.findByIdWithLock(...);   // 락은 잡히지만
payPeriod.subtractEwaAmount(amount);                               // 값은 락 획득 전의 것
```

Hibernate는 이미 영속성 컨텍스트에 있는 엔티티를 쿼리로 다시 읽어도 **기존 인스턴스를
반환하고 새로 읽은 값은 버린다.** 같은 트랜잭션 안에서 같은 엔티티가 다르게 보이면 안 되기
때문이다.

`FOR UPDATE`는 DB에서 실행되어 락은 걸린다. 그런데 돌아오는 자바 객체는 락 걸기 전에 읽은
그 인스턴스다. 락 대기 중에 다른 트랜잭션이 커밋했다면, **락은 잡았는데 낡은 값에서 빼게
된다.**

그래서 순서를 뒤집었다.

```java
EwaRequest ewaRequest = lockEwa(ewaRequestId);
// 검증보다 먼저 잠근다. validateEwa의 getEmployerId()가 PayPeriod 프록시를 초기화하는데,
// 그 뒤에 락을 잡으면 영속성 컨텍스트가 이미 들고 있는 값을 돌려주므로
// 락 획득 전의 금액에서 차감하게 된다.
PayPeriod payPeriod = payPeriodRepository.findByIdWithLock(ewaRequest.getPayPeriodId())
        .orElseThrow(() -> new NotFoundException("PayPeriod Not Found"));
validateEwa(ewaRequest, employerId);
```

`getPayPeriodId()`는 FK 값만 읽어 프록시를 초기화하지 않으므로 락 조회 전에 불러도 안전하다.

> **락은 대상 필드를 건드리기 전에 잡는다.**

---

## 포트를 어디까지 두었나

여기까지 하고 나면 자연스러운 질문이 나온다. 그럼 리포지토리도 포트로 빼야 하지 않나?

원래 정의로는 그렇다. 헥사고날에서 DB도 외부 시스템이고, 도메인 객체와 JPA 엔티티를 분리해
어댑터가 매핑하는 게 정석이다. 그러면 **이 글에서 다룬 문제가 애초에 생기지 않는다** —
도메인 객체는 프록시가 아니니 지연 로딩도, 준영속이라는 개념도 없다.

하지만 하지 않았다.

- 엔티티마다 도메인 객체 · 엔티티 · 매퍼 세 벌이 생긴다
- **더티 체킹을 잃는다.** `payPeriod.addEarnedAmount(...)` 한 줄로 끝나던 것이 명시적 `save`와
  매핑으로 바뀐다
- 조회 최적화(`@EntityGraph` 등)가 어댑터 안에 갇힌다

DB를 바꿀 계획이 없는데 이 비용을 내는 건 유연성값을 미리 지불하는 것이다. 반면 PG사와
펌뱅킹은 **목으로 개발하고 나중에 실연동한다는 전제가 처음부터 있었다.** 교체 가능성이
가정이 아니라 계획이었고, 지금도 `MockWageTransferAdapter`와 `FirmBankingWageTransferAdapter`가
공존한다.

그래서 정확히 말하면 **"교체 가능성이 실재하는 곳에만 포트/어댑터를 적용"**했다.
전면 헥사고날이 아니다.

대신 그 선택의 대가를 안다. 엔티티가 도메인을 겸하니 **"엔티티를 트랜잭션 경계 밖으로
넘기지 않는다"를 사람이 지켜야 한다.** 그리고 이 글이 그걸 지키지 못한 기록이다.

리포지토리까지 뺄 시점은 도메인 로직이 JPA 제약 때문에 뒤틀리기 시작할 때다. 지금은 아니다.

---

## 남은 것

컨텍스트 레코드는 결국 같은 말을 세 층위에서 하고 있다.

| 경계 | 넘기는 것 |
|---|---|
| 스레드 | `BulkSettlementItemContext` |
| 트랜잭션 | `EwaTransferContext` · `EwaTransferRetryContext` |
| 프로세스(외부 API) | `TransferAccount` |

**경계를 넘을 때 살아 있어야 하는 것은 값뿐이다.**

그런데 경계에는 시간도 있다. 트랜잭션보다 긴 흐름은 락으로 덮을 수 없다. 다음 글에서 다룬다.

---

### 참고

- PR #70 `refactor: make transfer port take account values instead of entities`
- PR #68 `fix: lock pay period row when updating amount totals`
- 코드: `TransferAccount`, `WageTransferPort`, `Worker.toTransferAccount`, `EwaTransferProcessor.createEwaTransfer`
