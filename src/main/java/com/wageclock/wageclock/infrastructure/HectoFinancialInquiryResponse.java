package com.wageclock.wageclock.infrastructure;

// 핵토파이낸셜 이체처리결과조회 응답 전문 (7100/100)
// 처리결과는 공통부 응답코드 또는 개별부 처리결과로 판단
// 처리결과가 처리 중 코드("PRCS")인 경우 다른 결과를 받을 때까지 재조회 필요
public record HectoFinancialInquiryResponse(
        String responseCode,   // 공통부 응답코드 (4자리)
        String processResult,  // 개별부 처리결과 (4자리, 은행별 응답코드집 확인)
        String normalAmount,   // 정상처리금액 (13자리)
        String failAmount      // 처리불능금액 (13자리)
) {}