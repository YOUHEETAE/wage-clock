package com.wageclock.wageclock.infrastructure;

public record PortOneVirtualAccountInfoResponse (String status, String id, Method method){
    public record Method(String bank, String accountNumber, String expiredAt, String remitteeName){}
}
