package com.smartroute.smartroute1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Primary
    public WebClient stravaClient() {
        return WebClient.builder()
                .baseUrl("https://www.strava.com")
                .build();
    }

    @Bean(name = "juliaWebClient")
    public WebClient juliaWebClient(
            @org.springframework.beans.factory.annotation.Value("${planner.julia.baseUrl:http://localhost:8081}")
            String baseUrl
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}

