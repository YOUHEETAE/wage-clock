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
동시 선지급 요청 제어  → 분산 락 (Redisson)
결제 상태 관리         → 상태 머신 (PENDING → APPROVED → PAID → REJECTED)
실제 결제 연동         → PaymentService 인터페이스 분리로 PG사 교체 가능한 구조 설계
```

---

## 도메인 모델

```
Employer (고용주)
  └─ Employment (고용 관계) ── Worker (근로자)
       └─ WorkSession (근무 세션 / 급여시계)
            └─ EwaRequest (선지급 요청)
```

## ERD

![ERD](docs/images/erd.png)

### 비즈니스 규칙

```
선지급 한도:     실시간 적립액 × 30% 로 수정     
선지급 잔액:     한도 - totalEwaAmount (누적 선지급액)
Worker는 여러 사업장에 동시 고용 가능 (Employment로 관리)
```

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| **언어** | Java 21 |
| **프레임워크** | Spring Boot 3.5 |
| **데이터베이스** | PostgreSQL 16 |
| **ORM** | Spring Data JPA (Hibernate) |
| **인증** | JWT |
| **결제** | Mock Payment Service (PG사 교체 가능한 구조) |
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
⬜ Phase 8: 외부 PG 연동
⬜ Phase 9: 고용주 대시보드 API
⬜ Phase 10: 동시성 검증 (JMeter)
⬜ Phase 11: React 프론트엔드 (급여시계 UI)
```

---

## 브랜치 전략

```
main    → 배포 가능한 최종 브랜치
dev     → 개발 통합 브랜치
feature → 기능 단위 개발 브랜치 (feature/xxx → dev PR)
```