package com.wageclock.wageclock.infrastructure;

public record PortOneCustomer (Name name){
    public record Name(String full){}
}
