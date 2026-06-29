# WageClock — 급여 선지급 서비스

> 시간제 노동자의 분 단위 근태를 기록하고,
> 적립된 급여를 즉시 선지급할 수 있는 임금 인프라 서비스

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5-6DB33F?style=flat&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-4169E1?style=flat&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)
![AWS](https://img.shields.io/badge/AWS_EC2-FF9900?style=flat&logo=amazonaws&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat&logo=githubactions&logoColor=white)

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

| 기능 | 설명 |
|------|------|
| 근태 기록 | 출근·퇴근·일시정지·재개, 분 단위 급여 실시간 적립 |
| 선지급 (EWA) | 적립액의 30% 한도 내에서 즉시 펌뱅킹 이체 |
| 일괄 정산 | 고용주 버튼 한 번으로 직원 N명 동시 송금 |
| PayPeriod 정산 | 월 단위 PayPeriod close, 실지급 월급 자동 계산 |
| 고용 이력 타임라인 | WorkSession·EWA·PayPeriod 이벤트를 커서 기반 조회 |

---

## 프로젝트 규모

| 항목 | 수치                        |
|------|---------------------------|
| API 엔드포인트 | 21개                       |
| JPA 엔티티 | 12개                       |
| 테스트 케이스 | 205개 (단위 + 통합, 35개 파일)    |
| 테스트 커버리지 | 81% (Line) / 75% (Branch) |

---

## 아키텍처

### 레이어 구조

```
Controller
    ↓
Service          ← 분산 락, 흐름 조율
    ↓
Processor        ← @Transactional DB 상태 전이
    ↓
Port (Interface) ← 외부 API 추상화
    ↓
Adapter          ← PortOne / 헥토파이낸셜 구현체
    ↓
Repository
    ↓
postgreSQL
```

> Service는 외부 I/O와 흐름 조율만 담당하고, DB 상태 전이는 Processor가 담당한다.
이를 통해 Spring Proxy 기반 @Transactional의 self-invocation 문제를 피하고,
트랜잭션 경계를 명확히 분리했다.

### 도메인 모델

```
Employer (고용주)
  └─ Employment (고용 관계) ── Worker (근로자)
       └─ PayPeriod (월 단위 정산 기간)
            └─ WorkSession (근무 세션 / 급여시계)
            └─ EwaRequest (선지급 요청)
                 └─ EwaTransfer (펌뱅킹 이체)
                      └─ EwaTransferFailureOutBoxEvent (재이체 Outbox)
```

### ERD

![ERD](docs/images/erd.png)

---

## 기술적 도전

### 동시성

- **Idempotency Key**: 네트워크 재시도로 인한 중복 요청 방지
- **Redisson 분산 락**: 동일 워커의 동시 선지급 요청을 서버 인스턴스 간에 차단
- **DB 비관적 락**: EWA 요청과 BulkSettlement 정산 간 PayPeriod 동시 수정 방지

### 외부 API

- **PortOne V2**: 일괄 정산용 가상계좌 발급 + 웹훅 수신
- **Mock 헥토파이낸셜 펌뱅킹**: EWA 직접 이체 + 일괄 정산 이체
- **외부 API와 트랜잭션 분리**: DB 커넥션 점유 방지를 위해 외부 API 호출과 @Transactional 분리

### 장애복구

- **Outbox 패턴**: 펌뱅킹 이체 실패 시 Scheduler 기반 자동 재시도
- **전문번호 사전생성**: 송금 전 messageNo를 DB에 저장해 네트워크 예외 시에도 이중 송금 방지
- **RETRYING / UNKNOWN 상태 분리**: 타행이체불능(재시도 가능)과 결과 불명확(조회 필요)을 명시적으로 구분

### 성능

- **커서 기반 페이징**: UNION ALL 구조에서 Offset 대신 timestamp 커서로 히스토리 조회
- **CompletableFuture 병렬 처리**: 일괄 정산 시 직원 N명 동시 펌뱅킹 이체
- **Sealed Interface**: 이체 결과를 타입 안전하게 분기, I/O와 DB 상태변화를 스레드 경계에서 분리

### 아키텍처

- **Hexagonal Architecture**: VirtualAccountPort / WageTransferPort 인터페이스로 PG사 교체 가능
- **Processor 패턴**: 분산 락 내부 self-call 문제 해결, @Transactional 경계 명확화

---

## 플로우

자세한 흐름은 다이어그램을 참고한다.

- [시퀀스 다이어그램](docs/diagrams/sequence-diagram.md)
- [상태전이 다이어그램](docs/diagrams/state-diagram.md)

---

## 도메인 규칙

```
선지급 한도:     (PayPeriod 적립액 + PAUSED 세션 적립액) × 30%
선지급 잔액:     한도 - totalEwaAmount (누적 선지급액)
실제 지급 월급:  확정 번 돈 - totalEwaAmount (월급 정산 시)
Worker는 여러 사업장에 동시 고용 가능 (Employment로 관리)
```

### 계산 시점 정리

| 항목 | 계산 시점 | 계산 방법 |
|------|-----------|-----------|
| **현재 번 돈** | EWA 요청할 때마다 | PAUSED면 스냅샷 반환, WORKING이면 `lastResumeAt` 기준 누적 |
| **확정 번 돈** | 퇴근(clockOut) 시 | 퇴근 시점의 현재 번 돈을 WorkSession에 저장 |
| **PayPeriod.totalEarnedAmount** | 퇴근(clockOut) 시에만 누적 | `+ WorkSession.earnedAmount` |
| **선지급 한도** | EWA 요청할 때마다 | `(totalEarnedAmount + PAUSED 세션 적립액) × 30% - totalEwaAmount` |
| **totalEwaAmount** | EWA 요청 시 증가 | `+ requestedAmount` |
| **totalEwaAmount** | EWA 거절 / 이체 실패 시 감소 | `- requestedAmount` |
| **실제 지급 월급** | 월급 정산 시 (PayPeriod close) | `확정 번 돈 - totalEwaAmount` |
| **EWA 펌뱅킹 이체** | 고용주 승인(initiateEwa) 즉시 | Mock 펌뱅킹 API 호출, 결과에 따라 상태 전이 |

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
| **인프라** | Docker, docker-compose, AWS EC2, Nginx |
| **CI/CD** | GitHub Actions (CI: 테스트/빌드, CD: GHCR 푸시 + EC2 자동 배포) |
| **빌드** | Gradle |

---

## 주요 개발 히스토리

- JWT 인증 + 근무 세션 및 급여 계산
- EWA 선지급 API (멱등성 키 + 분산 락)
- PortOne 가상계좌 연동 + Outbox 기반 장애 복구
- EWA 직접 펌뱅킹 전환 (즉시성 확보)
- Bulk Settlement (CompletableFuture 병렬 처리 + Outbox 재시도)
- messageNo 사전생성 + RETRYING/UNKNOWN 상태 통합
- Processor 패턴 + classify() 리팩토링
- 커서 기반 히스토리 페이징
- AWS EC2 배포 + GitHub Actions CD + Nginx

<details>
<summary>전체 로드맵 보기</summary>

```
✅ Phase 1: 프로젝트 세팅 (Spring Boot + PostgreSQL + JWT)
✅ Phase 2: 엔티티 설계 (Employer / Worker / Employment / WorkSession / EwaRequest)
✅ Phase 3: JWT 인증 (회원가입 / 로그인)
✅ Phase 4: 근무 세션 API (출근 / 퇴근 / 일시정지 / 재개 / 급여 계산)
✅ Phase 5: 선지급 API (멱등성)
✅ Phase 5.5: Redis 연동 (분산 락)
✅ Phase 6: PG 인터페이스 설계
✅ Phase 7: Payment History 설계
✅ Phase 8: PortOne 가상계좌 연동 (발급 + 웹훅 수신)
✅ Phase 9: EwaTransaction 거래 내역 기록
✅ Phase 10: Outbox 패턴 (장애복구 - Scheduler 기반)
✅ Phase 11: 정산 API (PayPeriod close + 실지급 월급 계산 + 새 PayPeriod 생성)
✅ Phase 12: 고용주 대시보드 + PayPeriod 요약 + 고용 이력 타임라인 (JdbcTemplate)
✅ Phase 13: 일괄 정산 (Mock 헥토파이낸셜 펌뱅킹 병렬 처리 + Outbox 재시도)
✅ Phase 14: EWA 리팩토링 (PortOne 가상계좌 제거 → 직접 펌뱅킹 이체 + VTIM/UNKNOWN 상태 관리 + RETRYING 분리 + Outbox 재이체)
✅ Phase 15: Settlement 리팩토링 (VTIM/UNKNOWN 상태 관리 + RETRYING 분리 + Outbox 재이체)
✅ Phase 16: messageNo 사전생성 + RETRYING·UNKNOWN 통합 구조 (EWA·Bulk 공통 패턴으로 추출)
✅ Phase 17: 네이밍 정리 (도메인 용어 통일 — 메서드·상태·이벤트명 일관성 확보)
✅ Phase 18: Swagger / OpenAPI 문서화 (springdoc-openapi, 전체 API 엔드포인트 명세)
✅ Phase 19: BulkSettlement 리팩토링 (이체 결과를 Sealed Interface로 타입 안전하게 분기, prepareTransfer 실패 → Retryable, 타임아웃을 개별 future로 이동, 워커 분류 버그 수정)
✅ Phase 20: EWA Transfer 리팩토링 (prepareTransfer 실패 → FAILED 분리, inquiryTransfer else절 추가, Outbox terminal state early exit으로 이중송금 방지)
✅ Phase 21: Outbox 리팩토링 (try-catch 분리로 issueMessageNo 실패 버그 수정, Processor 패턴으로 @Transactional 경계 분리, classify()로 결과 분기 캡슐화)
✅ Phase 22: JPA 더티 체킹 활용 (트랜잭션 내 불필요한 repository.save() 제거)
✅ Phase 23: history cursor 기반 조회 구현
✅ Phase 24: AWS EC2 배포 (docker-compose, GitHub Actions CD, GHCR)
✅ Phase 25: Nginx 리버스 프록시 (80포트, 스프링 앱 외부 직접 노출 제거)
⬜ Phase 26: React + TypeScript 프론트엔드 (핵심 플로우 동작 중심)
```

</details>

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
main    → 배포 가능한 최종 브랜치 (push 시 CD 자동 실행)
dev     → 통합 검증 브랜치 (feature 머지 후 검증, 완료 시 main으로)
feature → 기능 단위 개발 브랜치 (feature/xxx → dev PR)
```

---

## 라이센스

MIT License