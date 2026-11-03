package com.example.postaxvatsystem.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;

@Configuration
public class RateLimitConfig {

    @Value("${rate.limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Value("${rate.limit.requests-per-hour:1000}")
    private int requestsPerHour;

    @Bean
    public Supplier<BucketConfiguration> bucketConfiguration() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(requestsPerMinute, Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))))
                .addLimit(Bandwidth.classic(requestsPerHour, Refill.intervally(requestsPerHour, Duration.ofHours(1))))
                .build();
    }
}
