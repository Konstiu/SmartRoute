package com.smartroute.smartroute1.service.impl;

import com.smartroute.smartroute1.endpoint.dto.trainingplan.FitUserModelRequest;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.FitUserModelResponse;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.JuliaScoreTemplateRequest;
import com.smartroute.smartroute1.endpoint.dto.trainingplan.JuliaScoreTemplateResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JuliaPlannerClient {
    private final @Qualifier("juliaWebClient") WebClient juliaWebClient;
    private static final Logger log = LoggerFactory.getLogger(TrainingPlan7dServiceImpl.class);

    public Mono<Boolean> health() {
        return juliaWebClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(String.class)
                .map("ok"::equalsIgnoreCase)
                .timeout(Duration.ofSeconds(1))
                .onErrorReturn(false);
    }

    public Mono<JuliaScoreTemplateResponse> next7Days(JuliaScoreTemplateRequest req) {
        return juliaWebClient.post()
                .uri("/plan/next-7-days")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(JuliaScoreTemplateResponse.class)
                .timeout(Duration.ofSeconds(2));
    }

    @Value("${planner.julia.enabled:false}")
    private boolean enabled;

    public Optional<JuliaScoreTemplateResponse> scoreTemplate(JuliaScoreTemplateRequest req) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            JuliaScoreTemplateResponse resp = juliaWebClient.post()
                    .uri("/plan/score-template")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(JuliaScoreTemplateResponse.class)
                    .timeout(Duration.ofSeconds(3))
                    .onErrorResume(ex -> {
                        log.warn("Julia scoreTemplate failed, falling back: {}", ex.toString());
                        return Mono.empty();
                    })
                    .block();

            return Optional.ofNullable(resp);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<FitUserModelResponse> fitUserModel(FitUserModelRequest req) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            FitUserModelResponse resp = juliaWebClient.post()
                    .uri("/model/fit-user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            r -> r.bodyToMono(String.class).flatMap(msg -> Mono.error(new RuntimeException(msg))))
                    .bodyToMono(FitUserModelResponse.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();

            return Optional.ofNullable(resp);
        } catch (Exception e) {
            // optionally log.debug("fitUserModel failed", e);
            return Optional.empty();
        }
    }
}
