package com.wageclock.wageclock.infrastructure;

public record PortOneMethod(VirtualAccount virtualAccount) {
    public record VirtualAccount(String bank, Expiry expiry, Option option){
        public record Expiry(String dueDate){}
        public record Option(String type){}
    }
}
