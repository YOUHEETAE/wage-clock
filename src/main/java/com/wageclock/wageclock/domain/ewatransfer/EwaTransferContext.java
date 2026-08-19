package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.port.TransferAccount;

import java.math.BigDecimal;

/**
 * 이체에 필요한 값만 담아 트랜잭션 밖으로 내보낸다.
 * <p>
 * 펌뱅킹 호출은 최대 수십 초가 걸려 트랜잭션 안에 둘 수 없다. 그렇다고 엔티티를 넘기면
 * 영속성 컨텍스트가 닫힌 뒤에 연관관계를 타게 되므로, 트랜잭션 안에서 값으로 확정해 넘긴다.
 */
public record EwaTransferContext(Long ewaTransferId, BigDecimal amount, TransferAccount transferAccount) {
}
