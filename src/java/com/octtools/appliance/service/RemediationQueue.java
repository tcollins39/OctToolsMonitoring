package com.octtools.appliance.service;

import com.octtools.appliance.model.Appliance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.octtools.appliance.config.ConfigProperties.QUEUE_MAX_SIZE;

@Service
@Slf4j
public class RemediationQueue {
    
    private final ConcurrentLinkedQueue<Appliance> queue = new ConcurrentLinkedQueue<>();
    private final Set<String> currentlyProcessing = ConcurrentHashMap.newKeySet();
    private final int maxQueueSize;

    public RemediationQueue(@Value(QUEUE_MAX_SIZE) int maxQueueSize) {
        validateInputs(maxQueueSize);
        
        this.maxQueueSize = maxQueueSize;
        log.info("Initialized RemediationQueue with maxSize={}", maxQueueSize);
    }

    private void validateInputs(int maxQueueSize) {
        if (maxQueueSize <= 0) {
            throw new IllegalArgumentException("Max queue size must be positive, got: " + maxQueueSize);
        }
    }


    public void addAppliances(List<Appliance> appliances) {
        for (Appliance appliance : appliances) {
            if (currentlyProcessing.add(appliance.getId())) {
                // Successfully added to processing set, now add to queue
                if (queue.size() >= maxQueueSize) {
                    // Queue is full, skip this appliance - will be retried next cycle
                    currentlyProcessing.remove(appliance.getId());
                    log.warn("Queue full, skipping appliance {} - will retry next cycle", appliance.getId());
                    continue;
                }
                
                queue.offer(appliance);
                log.debug("Added appliance {} to remediation queue", appliance.getId());
            } else {
                log.debug("Appliance {} already being processed, skipping", appliance.getId());
            }
        }
        
        log.info("Queue size: {}, processing: {}", queue.size(), currentlyProcessing.size());
    }

    public Appliance pollOne() {
        Appliance appliance = queue.poll();
        if (appliance != null) {
            log.debug("Polled appliance {} for processing", appliance.getId());
        }
        return appliance;
    }

    public void markCompleted(String applianceId) {
        boolean removed = currentlyProcessing.remove(applianceId);
        if (removed) {
            log.debug("Marked appliance {} as completed", applianceId);
        } else {
            log.warn("Attempted to mark unknown appliance {} as completed", applianceId);
        }
    }
}
