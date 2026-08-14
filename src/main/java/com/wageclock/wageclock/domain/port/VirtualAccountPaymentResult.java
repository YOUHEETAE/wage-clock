package com.wageclock.wageclock.domain.port;

import java.math.BigDecimal;

/**
 * 결제 상태 재조회 결과.
 * 웹훅 페이로드를 신뢰하지 않고 PG에 직접 물어본 값이므로, 정산 진행 여부는 이것으로 판단한다.
 */
public record VirtualAccountPaymentResult(PaymentStatus status, BigDecimal paidAmount) {
}
