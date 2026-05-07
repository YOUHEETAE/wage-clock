package com.wageclock.wageclock.infrastructure;


public record PortOneVirtualAccountRequest (String storeId, String channelKey, String orderName,  Amount amount, String currency,
                                            PortOneMethod method, PortOneCustomer customer) {
    public record Amount(Long total){}
}
