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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.octtools.appliance.config.ConfigProperties.PROCESSING_THREAD_POOL_SIZE;
import static com.octtools.appliance.config.ConfigProperties.PROCESSING_ACTOR_EMAIL;

@Service
@Slf4j
public class RemediationProcessor {
    
    // Process queue every 100ms for high-throughput processing
    private static final int QUEUE_PROCESSING_DELAY_MS = 100;
    
    private final ApplianceApiClient apiClient;
    private final RemediationQueue remediationQueue;
    private final OperationRepository operationRepository;
    private final ExecutorService processingExecutor;

    public RemediationProcessor(
            ApplianceApiClient apiClient,
            RemediationQueue remediationQueue,
            OperationRepository operationRepository,
            @Value(PROCESSING_THREAD_POOL_SIZE) int threadPoolSize,
            @Value(PROCESSING_ACTOR_EMAIL) String actorEmail) {
        
        validateInputs(threadPoolSize, actorEmail);
        
        this.apiClient = apiClient;
        this.remediationQueue = remediationQueue;
        this.operationRepository = operationRepository;
        this.processingExecutor = new ThreadPoolExecutor(
            threadPoolSize, 
            threadPoolSize, 
            0L, 
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(50),
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

    @Scheduled(fixedDelay = QUEUE_PROCESSING_DELAY_MS)
    public void processRemediationQueue() {
        Appliance appliance = remediationQueue.pollOne();
        
        if (appliance == null) {
            return;
        }
        
        log.debug("Processing appliance: {}", appliance.getId());
        
        try {
            CompletableFuture.runAsync(() -> processAppliance(appliance), processingExecutor);
        } catch (RejectedExecutionException e) {
            log.warn("Executor queue full, re-queuing appliance: {}", appliance.getId());
            remediationQueue.markCompleted(appliance.getId());
            remediationQueue.addAppliances(List.of(appliance));
            log.info("METRIC: appliance.processing.queue.rejection.count=1");
        }
    }

    private void processAppliance(Appliance appliance) {
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
            
            log.info("METRIC: appliance.processing.success.count=1");
            
        } finally {
            // Always remove from processing set, even on failure
            remediationQueue.markCompleted(applianceId);
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
