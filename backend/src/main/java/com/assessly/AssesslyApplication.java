package com.assessly;

import com.assessly.infrastructure.redis.RedisAttemptProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(RedisAttemptProperties.class)
@EnableScheduling
public class AssesslyApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssesslyApplication.class, args);
    }
}
