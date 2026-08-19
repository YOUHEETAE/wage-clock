package com.wageclock.wageclock.domain.port;

/**
 * 결제 상태에 대한 도메인 어휘.
 * PG사별 상태 코드는 어댑터에서 이 셋 중 하나로 번역한다.
 */
public enum PaymentStatus {
    /** 입금 완료 — 정산을 진행한다 */
    PAID,
    /** 결제 무산 (실패·취소·기한만료) — 정산을 취소한다 */
    FAILED,
    /** 아직 입금 전 — 아무것도 하지 않고 다음 확인을 기다린다 */
    PENDING
}
