package com.wageclock.wageclock.infrastructure;

public record PortOneVirtualAccountInfoResponse (String status, String id, Method method, Amount amount){
    public record Method(String bank, String accountNumber, String expiredAt, String remitteeName){}
    // 결제 금액. total은 요청한 총액, paid는 실제 입금된 금액이다.
    public record Amount(Long total, Long paid){}
}
