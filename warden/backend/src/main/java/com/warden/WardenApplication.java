package com.warden;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Login is handled by {@link com.warden.auth.service.AuthService} and requests are authenticated by
 * {@link com.warden.security.filter.JwtAuthenticationFilter}, so Spring Boot's default in-memory user
 * (the "Using generated security password" one) is not needed.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling // RefreshTokenCleanupJob
public class WardenApplication {

    public static void main(String[] args) {
        SpringApplication.run(WardenApplication.class, args);
    }
}
