package com.octtools.appliance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import static com.octtools.appliance.config.ConfigProperties.API_BASE_URL;
import static com.octtools.appliance.config.ConfigProperties.API_AUTH_HEADER;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient applianceApiWebClient(
            @Value(API_BASE_URL) String baseUrl,
            @Value(API_AUTH_HEADER) String authHeader) {
        
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", authHeader)
                .build();
    }
}
