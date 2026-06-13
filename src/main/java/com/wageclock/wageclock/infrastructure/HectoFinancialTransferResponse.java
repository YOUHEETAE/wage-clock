package com.wageclock.wageclock.infrastructure;

// 핵토파이낸셜 당타행 지급이체 응답 전문 (2100/100)
// 응답코드 "0000" = 정상
// "VTIM" = 응답시간초과 → 7000/100(이체처리결과조회)으로 결과 재확인 필수
public record HectoFinancialTransferResponse(
        String responseCode,  // 응답코드 (4자리)
        String messageNo      // 전문번호 (6자리)
) {

}