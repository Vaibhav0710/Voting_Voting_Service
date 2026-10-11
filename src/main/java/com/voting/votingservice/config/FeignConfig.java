package com.voting.votingservice.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Feign client scanning lives here (not on the application class) so web-slice tests don't need Feign.
 */
@Configuration
@EnableFeignClients(basePackages = "com.voting.votingservice.client")
public class FeignConfig {
}
