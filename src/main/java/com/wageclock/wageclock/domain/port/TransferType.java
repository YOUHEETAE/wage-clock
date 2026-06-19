package com.wageclock.wageclock.domain.port;

import java.util.Arrays;

public enum TransferType {
    EWA("1"),
    BULK_SETTLEMENT("2");

    private final String prefix;

    TransferType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public static TransferType fromTransferId(String transferId) {
        return Arrays.stream(values())
                .filter(type -> transferId.startsWith(type.prefix))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown transferId prefix: " + transferId));
    }
}