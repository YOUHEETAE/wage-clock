package com.wageclock.wageclock.infrastructure;


public record PortOneVirtualAccountResponse (InstancePaymentSummary payment, String PaymentId){
    public record InstancePaymentSummary(String pgTxId, String paidAt) {
    }
}
