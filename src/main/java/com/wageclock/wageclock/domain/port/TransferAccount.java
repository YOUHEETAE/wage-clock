package com.wageclock.wageclock.domain.port;

/**
 * 이체 전문에 실리는 수취인 계좌. 포트가 엔티티 대신 이 값을 받는다.
 * <p>
 * 엔티티를 넘기면 이체 시점(트랜잭션 밖)에 연관관계를 타게 되어 지연 로딩이 일어나고,
 * 호출부마다 "미리 초기화해두는" 방식이 제각각이 된다. 값으로 받으면 뽑는 일이
 * 반드시 트랜잭션 안에서 끝나므로 그 부류가 구조적으로 불가능해진다.
 */
public record TransferAccount(String bankCode, String accountNumber, String holderName) {

    /**
     * 계좌 등록은 회원가입과 분리되어 있어 비어 있을 수 있다.
     * 비어 있으면 은행이 거부하므로 이체 전에 걸러낸다.
     */
    public boolean isRegistered() {
        return bankCode != null && accountNumber != null && holderName != null;
    }
}
