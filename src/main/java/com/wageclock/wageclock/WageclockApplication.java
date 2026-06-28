package com.wageclock.wageclock;

import com.wageclock.wageclock.infrastructure.HectoFinancialProperties;
import com.wageclock.wageclock.infrastructure.PortOneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({PortOneProperties.class, HectoFinancialProperties.class})
public class WageclockApplication {

    public static void main(String[] args) {
        SpringApplication.run(WageclockApplication.class, args);
    }

}
