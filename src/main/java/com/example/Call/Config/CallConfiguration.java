package com.example.Call.Config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(
    CallProperties.class
)
public class CallConfiguration {

    /*
     * A shared clock makes duration, timeout and expiration
     * calculations consistent and testable.
     */
    @Bean
    public Clock callClock() {
        return Clock.systemUTC();
    }
}