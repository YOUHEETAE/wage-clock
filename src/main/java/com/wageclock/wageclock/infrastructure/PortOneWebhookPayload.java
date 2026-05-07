package com.wageclock.wageclock.infrastructure;

public record PortOneWebhookPayload (String type, String timestamp, Data data){
    public record Data(String storeId, String paymentId, String transactionId){}
}
