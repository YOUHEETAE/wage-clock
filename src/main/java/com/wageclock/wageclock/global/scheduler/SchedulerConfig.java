package com.wageclock.wageclock.global.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enable", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {
}
