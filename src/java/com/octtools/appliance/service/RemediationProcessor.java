package com.octtools.appliance.service;

import com.octtools.appliance.client.ApplianceApiClient;
import com.octtools.appliance.model.Appliance;
import com.octtools.appliance.model.Operation;
import com.octtools.appliance.model.api.DrainResponse;
import com.octtools.appliance.model.api.RemediateResponse;
import com.octtools.appliance.repository.OperationRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.octtools.appliance.config.ConfigProperties.PROCESSING_THREAD_POOL_SIZE;
import static com.octtools.appliance.config.ConfigProperties.PROCESSING_ACTOR_EMAIL;

@Service
@Slf4j
public class RemediationProcessor {
    
    // Processing Configuration Constants
    private static final int PROCESSING_QUEUE_SIZE = 2500;
    
    private final ApplianceApiClient apiClient;
    private final OperationRepository operationRepository;
    private final ExecutorService processingExecutor;

    public RemediationProcessor(
            ApplianceApiClient apiClient,
            OperationRepository operationRepository,
            @Value(PROCESSING_THREAD_POOL_SIZE) int threadPoolSize,
            @Value(PROCESSING_ACTOR_EMAIL) String actorEmail) {
        
        validateInputs(threadPoolSize, actorEmail);
        
        this.apiClient = apiClient;
        this.operationRepository = operationRepository;
        this.processingExecutor = new ThreadPoolExecutor(
            threadPoolSize, 
            threadPoolSize, 
            0L, 
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(PROCESSING_QUEUE_SIZE),
            new ThreadPoolExecutor.AbortPolicy()
        );
        
        log.info("Initialized RemediationProcessor with threadPoolSize={}, actor={}", 
                threadPoolSize, actorEmail);
    }

    private void validateInputs(int threadPoolSize, String actorEmail) {
        if (threadPoolSize <= 0) {
            throw new IllegalArgumentException("Thread pool size must be positive, got: " + threadPoolSize);
        }
        if (actorEmail == null || actorEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Actor email cannot be null or empty");
        }
    }

    public void processAppliance(Appliance appliance) {
        try {
            processingExecutor.submit(() -> processApplianceInternal(appliance));
        } catch (RejectedExecutionException e) {
            log.warn("Executor queue full, skipping appliance {} - will retry next cycle", appliance.getId());
            log.info("METRIC: appliance.processing.queue_full.count=1");
        }
    }

    private void processApplianceInternal(Appliance appliance) {
        String applianceId = appliance.getId();
        log.debug("Processing appliance: {}", applianceId);
        
        try {
            // Step 1: Drain the appliance (with retry in API client)
            DrainResponse drainResponse = apiClient.drainAppliance(applianceId);
            recordDrainOperation(applianceId, drainResponse);
            
            // Step 2: Remediate the appliance (with retry in API client)
            RemediateResponse remediateResponse = apiClient.remediateAppliance(applianceId);
            recordRemediateOperation(applianceId, remediateResponse);
            
            log.info("Successfully processed appliance {}: drain={}, remediation={}", 
                    applianceId, drainResponse.getDrainId(), remediateResponse.getRemediationId());
            
            log.info("METRIC: appliance.processing.success.ratio=1");
        } catch (Exception e) {
            log.error("Failed to process appliance {}: {}", applianceId, e.getMessage());
            log.info("METRIC: appliance.processing.success.ratio=0");
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down RemediationProcessor");
        processingExecutor.shutdown();
        
        try {
            if (!processingExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("Processing executor did not terminate gracefully, forcing shutdown");
                processingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for executor shutdown");
            processingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private void recordDrainOperation(String applianceId, DrainResponse drainResponse) {
        Operation drainOperation = Operation.builder()
            .applianceId(applianceId)
            .operationType("DRAIN")
            .processedAt(Instant.now())
            .drainId(drainResponse.getDrainId())
            .estimatedTimeToDrain(drainResponse.getEstimatedTimeToDrain())
            .build();
        operationRepository.save(drainOperation);
    }
    
    private void recordRemediateOperation(String applianceId, RemediateResponse remediateResponse) {
        Operation remediateOperation = Operation.builder()
            .applianceId(applianceId)
            .operationType("REMEDIATE")
            .processedAt(Instant.now())
            .remediationId(remediateResponse.getRemediationId())
            .remediationResult(remediateResponse.getRemediationResult())
            .build();
        operationRepository.save(remediateOperation);
    }
}
