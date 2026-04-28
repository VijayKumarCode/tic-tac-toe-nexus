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

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
