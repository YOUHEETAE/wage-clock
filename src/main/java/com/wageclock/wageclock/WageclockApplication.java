package com.wageclock.wageclock;

import com.wageclock.wageclock.infrastructure.PortOneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableConfigurationProperties(PortOneProperties.class)
public class WageclockApplication {

    public static void main(String[] args) {
        SpringApplication.run(WageclockApplication.class, args);
    }

}
