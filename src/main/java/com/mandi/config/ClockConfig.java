package com.mandi.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dates follow India Standard Time: an import at 11 pm IST belongs to that day,
 * not the next UTC day. Everything date-related reads this clock, which also lets tests pin the date.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
