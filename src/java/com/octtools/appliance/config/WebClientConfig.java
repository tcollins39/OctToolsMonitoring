package com.octtools.appliance.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.octtools.appliance.config.ConfigProperties.API_BASE_URL;
import static com.octtools.appliance.config.ConfigProperties.API_AUTH_HEADER;

@Configuration
public class WebClientConfig {

    // HTTP Client Timeout Constants
    private static final int CONNECTION_TIMEOUT_MILLIS = 5000;
    private static final int RESPONSE_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 5;

    @Bean
    public WebClient applianceApiWebClient(
            @Value(API_BASE_URL) String baseUrl,
            @Value(API_AUTH_HEADER) String authHeader) {
        
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECTION_TIMEOUT_MILLIS)
                .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                .doOnConnected(conn -> 
                    conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)));
        
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", authHeader)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
