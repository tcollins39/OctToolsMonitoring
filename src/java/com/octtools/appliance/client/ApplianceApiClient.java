package com.octtools.appliance.client;

import com.octtools.appliance.model.api.AppliancePageResponse;
import com.octtools.appliance.model.api.DrainRequest;
import com.octtools.appliance.model.api.DrainResponse;
import com.octtools.appliance.model.api.RemediateRequest;
import com.octtools.appliance.model.api.RemediateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;

import static com.octtools.appliance.config.ConfigProperties.API_TIMEOUT_SECONDS;
import static com.octtools.appliance.config.ConfigProperties.PROCESSING_ACTOR_EMAIL;

@Component
@Slf4j
public class ApplianceApiClient {

    // Retry configuration: Explicit values for clarity and control
    private static final int OPERATION_MAX_RETRY_ATTEMPTS = 3;
    private static final int COLLECTION_MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_INITIAL_DELAY_MS = 500;
    private static final double RETRY_BACKOFF_MULTIPLIER = 1.5;

    private final WebClient webClient;
    private final String actorEmail;
    private final int timeoutSeconds;

    public ApplianceApiClient(
            WebClient webClient,
            @Value(API_TIMEOUT_SECONDS) int timeoutSeconds,
            @Value(PROCESSING_ACTOR_EMAIL) String actorEmail) {
        
        validateInputs(webClient, timeoutSeconds, actorEmail);
        
        this.webClient = webClient;
        this.actorEmail = actorEmail;
        this.timeoutSeconds = timeoutSeconds;
        
        log.info("Initialized ApplianceApiClient");
    }

    private void validateInputs(WebClient webClient, int timeoutSeconds, String actorEmail) {
        if (webClient == null) {
            throw new IllegalArgumentException("WebClient cannot be null");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        if (actorEmail == null || actorEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Actor email cannot be null or empty");
        }
    }

    // Retry: 5 attempts, 500ms delay, 1.5x multiplier (more retries for pagination since failure breaks entire collection)
    @Retryable(
            maxAttempts = COLLECTION_MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = RETRY_INITIAL_DELAY_MS, multiplier = RETRY_BACKOFF_MULTIPLIER)
    )
    public AppliancePageResponse getAppliances(String after, int pageSize) {
        Instant startTime = Instant.now();
        log.debug("Fetching appliances with after: {}, pageSize: {}", after, pageSize);

        try {
            var uriBuilder = webClient.get()
                    .uri(uriBuilder1 -> {
                        var builder = uriBuilder1.path("/api/1.0/appliances")
                                .queryParam("first", pageSize);
                        if (after != null) {
                            builder.queryParam("after", after);
                        }
                        return builder.build();
                    });

            AppliancePageResponse response = uriBuilder
                    .retrieve()
                    .bodyToMono(AppliancePageResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
            log.debug("Successfully fetched {} appliances",
                    response != null && response.getData() != null ? response.getData().size() : 0);

            // Emit API metrics
            log.debug("METRIC: api.get_appliances.latency.ms={}", latencyMs);
            log.debug("METRIC: api.get_appliances.success.ratio=1");

            return response;

        } catch (WebClientResponseException e) {
            long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
            log.warn("Failed to fetch appliances (attempt will retry): status={}, response={}", e.getStatusCode(), e.getResponseBodyAsString());

            // Emit API metrics
            log.debug("METRIC: api.get_appliances.latency.ms={}", latencyMs);
            log.debug("METRIC: api.get_appliances.success.ratio=0");
            log.debug("METRIC: api.get_appliances.failure.count=1");

            throw e;
        }
    }

    // Retry: 3 attempts, 500ms delay, 1.5x multiplier (don't retry 404s)
    @Retryable(
            noRetryFor = {WebClientResponseException.NotFound.class},
            maxAttempts = OPERATION_MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = RETRY_INITIAL_DELAY_MS, multiplier = RETRY_BACKOFF_MULTIPLIER)
    )
    public DrainResponse drainAppliance(String applianceId) {
        Instant startTime = Instant.now();
        log.debug("Draining appliance: {}", applianceId);

        try {
            DrainRequest request = new DrainRequest();
            request.setReason(String.format("Appliance %s detected as stale - automated drain", applianceId));
            request.setActor(actorEmail);

            DrainResponse response = webClient.post()
                    .uri("/api/1.0/appliances/{id}/drain", applianceId)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(DrainResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
            log.info("Successfully drained appliance {}: drainId={}", applianceId, 
                    response != null ? response.getDrainId() : null);
            
            // Emit API metrics
            log.debug("METRIC: api.drain_appliance.latency.ms={}", latencyMs);
            log.debug("METRIC: api.drain_appliance.success.ratio=1");
            
            return response;
            
        } catch (WebClientResponseException.NotFound e) {
            long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
            log.warn("Appliance {} no longer exists, skipping drain", applianceId);
            
            // Emit API metrics (404 is not a failure, it's expected)
            log.debug("METRIC: api.drain_appliance.latency.ms={}", latencyMs);
            log.debug("METRIC: api.drain_appliance.not_found.count=1");
            
            throw e;
        } catch (WebClientResponseException e) {
            long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
            log.warn("Failed to drain appliance {} (will retry): status={}, response={}", 
                    applianceId, e.getStatusCode(), e.getResponseBodyAsString());
            
            // Emit API metrics
            log.debug("METRIC: api.drain_appliance.latency.ms={}", latencyMs);
            log.debug("METRIC: api.drain_appliance.success.ratio=0");
            log.debug("METRIC: api.drain_appliance.failure.count=1");
            
            throw e;
        }
    }

    // Retry: 3 attempts, 500ms delay, 1.5x multiplier (don't retry 404s)
    @Retryable(
            noRetryFor = {WebClientResponseException.NotFound.class},
            maxAttempts = OPERATION_MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = RETRY_INITIAL_DELAY_MS, multiplier = RETRY_BACKOFF_MULTIPLIER)
    )
    public RemediateResponse remediateAppliance(String applianceId) {
        Instant startTime = Instant.now();
        log.debug("Remediating appliance: {}", applianceId);
        
        try {
            RemediateRequest request = new RemediateRequest();
            request.setReason(String.format("Appliance %s remediation after drain", applianceId));
            request.setActor(actorEmail);

            RemediateResponse response = webClient.post()
                    .uri("/api/1.0/appliances/{id}/remediate", applianceId)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RemediateResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
            log.info("Successfully remediated appliance {}: remediationId={}, result={}", 
                    applianceId, 
                    response != null ? response.getRemediationId() : null,
                    response != null ? response.getRemediationResult() : null);
            
            // Emit API metrics
            log.debug("METRIC: api.remediate_appliance.latency.ms={}", latencyMs);
            log.debug("METRIC: api.remediate_appliance.success.ratio=1");
            
            return response;
            
        } catch (WebClientResponseException.NotFound e) {
            long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
            log.warn("Appliance {} no longer exists, skipping remediation", applianceId);
            
            // Emit API metrics (404 is not a failure, it's expected)
            log.debug("METRIC: api.remediate_appliance.latency.ms={}", latencyMs);
            log.debug("METRIC: api.remediate_appliance.not_found.count=1");
            
            throw e;
        } catch (WebClientResponseException e) {
            long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
            log.warn("Failed to remediate appliance {} (attempt will retry): status={}, response={}", 
                    applianceId, e.getStatusCode(), e.getResponseBodyAsString());
            
            // Emit API metrics
            log.debug("METRIC: api.remediate_appliance.latency.ms={}", latencyMs);
            log.debug("METRIC: api.remediate_appliance.success.ratio=0");
            log.debug("METRIC: api.remediate_appliance.failure.count=1");
            
            throw e;
        }
    }
}
