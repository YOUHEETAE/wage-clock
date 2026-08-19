package com.wageclock.wageclock.domain.port;


import java.math.BigDecimal;

/**
 * 펌뱅킹 이체 포트. 주고받는 타입은 전부 값 객체다 —
 * 어댑터가 도메인 엔티티를 알면 영속성 관심사가 인프라로 새어나간다.
 */
public interface WageTransferPort {
    String prepareTransfer(TransferType type);
    WageTransferResult transfer(TransferAccount transferAccount, BigDecimal amount, String messageNo);
    WageTransferResult inquireTransfer(String pendingMessageNo);
}
