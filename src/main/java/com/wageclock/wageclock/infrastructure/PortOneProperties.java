package com.wageclock.wageclock.infrastructure;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "portone")
public class PortOneProperties {
    private final String apiSecret;
    private final String storeId;
    private final String channelKey;
    public PortOneProperties(String apiSecret, String storeId, String channelKey) {
        this.apiSecret = apiSecret;
        this.storeId = storeId;
        this.channelKey = channelKey;
    }
}

