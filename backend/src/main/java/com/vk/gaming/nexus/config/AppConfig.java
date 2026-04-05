package com.vk.gaming.nexus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Data
@Component
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private String baseUrl;
    private String mailFrom;

    /*
     * BUG FIX: OtpService was instantiating RestTemplate with `new RestTemplate()`
     * as a final field. This meant:
     *  - No Spring interceptors could be applied
     *  - RestTemplate could not be mocked in unit tests
     *  - Connection pool / timeout settings could not be configured centrally
     *
     * Fix: declare RestTemplate as a @Bean here so it's a singleton managed
     * by Spring, injectable wherever needed, and easily configurable.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
