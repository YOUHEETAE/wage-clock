package com.wageclock.wageclock.infrastructure;

// 핵토파이낸셜 당타행 지급이체 전문 (2000/100)
// 공통부 100byte + 개별부 200byte = 총 300byte 고정길이 전문
public record HectoFinancialTransferRequest(
        // 공통부
        String companyNo,           // 업체번호 (12자리)
        String bankCode,            // 은행코드 (3자리)
        String messageNo,           // 전문번호 (6자리, 일별 unique)
        String transmitDate,        // 전송일자 (YYYYMMDD)
        String transmitTime,        // 전송시간 (HHmmss)
        // 개별부 - 요청
        String withdrawAccountNo,   // 출금계좌번호 (15자리, 모계좌)
        String accountPwd,          // 통장비밀번호 (8자리)
        String reconfirmCode,       // 복기부호 (6자리)
        long amount,                // 출금금액 (13자리)
        String depositBankCode,     // 입금은행코드 (3자리)
        String depositAccountNo,    // 입금계좌번호 (15자리)
        String remark               // 적요 (14자리, 수취인 통장 인자)
) {}