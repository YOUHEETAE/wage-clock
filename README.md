# WageClock — 급여 선지급 서비스

> 시간제 노동자의 분 단위 근태를 기록하고,
> 적립된 급여를 즉시 선지급할 수 있는 임금 인프라 서비스

---

## 배경

아르바이트·프리랜서·일용직 등 시간제 노동 현장에서는 두 가지 문제가 반복된다.

```
문제 1: 급여 분쟁
  근로시간이 구두·메신저에 의존해 기록됨
  → 퇴근 후 "얼마나 일했는가"가 해석의 영역이 됨

문제 2: 현금흐름 불일치
  소득은 월 1회, 지출은 매일 발생
  → 이미 일한 만큼의 급여에 접근할 수 없음
```

→ 분 단위 근태 기록 + 적립 급여 선지급으로 두 문제를 동시에 해결한다.

---

## 핵심 기능

```
출근 → 급여시계 시작 (분 단위 적립)
퇴근 → 급여 확정
선지급 요청 → 근무 중/퇴근 후 모두 가능, 적립액의 30% 한도 내에서 즉시 지급    
고용주 대시보드 → 직원별 급여 현황 실시간 조회
```

---

## 기술적 도전

```
선지급 중복 요청 방지  → 멱등성 (Idempotency Key)
동시 선지급 요청 제어  → 분산 락 (Redisson) + Pessimistic Lock (DB 레벨)
결제 상태 관리         → 상태 머신 (READY → PROCESSING → COMPLETED / FAILED)
가상계좌 결제 연동     → PortOne V2 REST API (가상계좌 발급 + 웹훅 수신)
실제 송금 처리         → 오픈뱅킹 미지원으로 가상 계좌 Mock 구현
                          (실서비스 전환 시 오픈뱅킹 API 연동 필요)
PG사 교체 가능 구조    → VirtualAccountPort 인터페이스 분리 (Hexagonal Architecture)
외부 API 트랜잭션 분리 → DB 커넥션 풀 고갈 방지를 위해 외부 API 호출과 @Transactional 분리
거래 내역 기록         → balance 없이 EwaTransaction으로 거래 이력만 관리
                          (실서비스 전환 시 오픈뱅킹 API 연동으로 실제 송금 처리)
```

---

## 결제 플로우

```
근로자 EWA 요청 (특정 금액)
  → 선지급 가능 금액 검증 (적립액 × 30% 한도 내)
  → PENDING 상태로 요청 저장
  → 고용주 승인 (initiateEwa)
  → 요청 금액만큼 고용주에게 가상계좌 발급 (PortOne) — Payment READY → PROCESSING
  → 고용주가 해당 가상계좌에 입금
  → PortOne 웹훅으로 입금 확인 (Transaction.Paid) — Payment COMPLETED, EWA APPROVED
  → EwaTransaction 기록 (거래 내역 저장)
```

---

## 도메인 모델

```
Employer (고용주)
  └─ Employment (고용 관계) ── Worker (근로자)
       └─ WorkSession (근무 세션 / 급여시계)
            └─ EwaRequest (선지급 요청)
                 └─ Payment (결제)
                 │    └─ PaymentHistory (결제 히스토리)
                 └─ EwaTransaction (거래 내역)
```

## ERD

![ERD](docs/images/erd.png)

### 비즈니스 규칙

```
선지급 한도:     실시간 적립액 × 30%
선지급 잔액:     한도 - totalEwaAmount (누적 선지급액)
실제 지급 월급:  확정 번 돈 - totalEwaAmount (월급 정산 시)
Worker는 여러 사업장에 동시 고용 가능 (Employment로 관리)
```

### 계산 시점 정리

| 항목 | 계산 시점 | 계산 방법 |
|------|-----------|-----------|
| **현재 번 돈** | EWA 요청할 때마다 | `(현재시각 - 출근시각) × 시급 / 3600` |
| **확정 번 돈** | 퇴근 시 | 퇴근 시점의 현재 번 돈을 DB에 저장 |
| **선지급 한도** | EWA 요청할 때마다 | `현재 번 돈 × 30% - totalEwaAmount` |
| **totalEwaAmount** | EWA 요청 시 증가 | `+ requestedAmount` |
| **totalEwaAmount** | EWA 거절 / 결제 실패 시 감소 | `- requestedAmount` |
| **실제 지급 월급** | 월급 정산 시 (미구현) | `확정 번 돈 - totalEwaAmount` |
| **가상계좌 발급** | 고용주 승인(initiateEwa) 시 | PortOne API 호출 |
| **근로자 가상계좌 입금** | 웹훅 수신 시 | 입금 확인 후 Mock 처리 |

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| **언어** | Java 21 |
| **프레임워크** | Spring Boot 3.5 |
| **데이터베이스** | PostgreSQL 16 |
| **ORM** | Spring Data JPA (Hibernate) |
| **인증** | JWT |
| **결제** | PortOne V2 (가상계좌 실연동) + Mock 송금 |
| **분산 락** | Redis (Redisson) |  
| **인프라** | Docker |
| **빌드** | Gradle |

---

## 로드맵

```
✅ Phase 1: 프로젝트 세팅 (Spring Boot + PostgreSQL + JWT)
✅ Phase 2: 엔티티 설계 (Employer / Worker / Employment / WorkSession / EwaRequest)
✅ Phase 3: JWT 인증 (회원가입 / 로그인)
✅ Phase 4: 근무 세션 API (출근 / 퇴근 / 급여 계산)
✅ Phase 5: 선지급 API (멱등성)
✅ Phase 5.5: redis 연동 (분산 락)
✅ Phase 6: PG 인터페이스 설계
✅ Phase 7: Payment History 설계
✅ Phase 8: PortOne 가상계좌 연동 (발급 + 웹훅 수신)
✅ Phase 9: EwaTransaction 거래 내역 기록
⬜ Phase 10: Outbox 패턴 (장애복구 - Scheduler 기반)
⬜ Phase 11: 고용주 대시보드 API
⬜ Phase 12: 동시성 검증 (JMeter)
⬜ Phase 13: Kafka (분산 서버 도입 후 Outbox 처리 주체 교체)
⬜ Phase 14: React 프론트엔드 (급여시계 UI)
```

---

## 수익 모델

```
선지급 건당 수수료 (근로자 부담)
고용주는 무료로 선지급 인프라를 제공받고,
근로자는 월급날 전에 적립 급여에 즉시 접근하는 대신 소액 수수료 부담
```

---

## 브랜치 전략

```
main    → 배포 가능한 최종 브랜치
dev     → 개발 통합 브랜치
feature → 기능 단위 개발 브랜치 (feature/xxx → dev PR)
```