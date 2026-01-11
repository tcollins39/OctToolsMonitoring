package com.octtools.appliance.service;

import com.octtools.appliance.client.ApplianceApiClient;
import com.octtools.appliance.model.Appliance;
import com.octtools.appliance.model.api.AppliancePageResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.octtools.appliance.config.ConfigProperties.API_PAGE_SIZE;
import static com.octtools.appliance.config.ConfigProperties.PROCESSING_STALE_THRESHOLD_MINUTES;

@Service
@Slf4j
public class ApplianceMonitorService {
    
    // Monitor appliances every 5 minutes as per requirements
    private static final int MONITORING_INTERVAL_MS = 300000;
    
    private static final String LIVE_STATUS = "LIVE";
    
    private int totalAppliancesProcessed;
    private int staleAppliancesFound;
    
    private final ApplianceApiClient apiClient;
    private final RemediationProcessor remediationProcessor;
    private final int pageSize;
    private final Duration staleThreshold;

    public ApplianceMonitorService(
            ApplianceApiClient apiClient,
            RemediationProcessor remediationProcessor,
            @Value(API_PAGE_SIZE) int pageSize,
            @Value(PROCESSING_STALE_THRESHOLD_MINUTES) int staleThresholdMinutes) {
        
        validateInputs(pageSize, staleThresholdMinutes);
        
        this.apiClient = apiClient;
        this.remediationProcessor = remediationProcessor;
        this.pageSize = pageSize;
        this.staleThreshold = Duration.ofMinutes(staleThresholdMinutes);
        
        log.info("Initialized ApplianceMonitorService with pageSize={}, staleThreshold={}min", 
                pageSize, staleThresholdMinutes);
    }

    private void validateInputs(int pageSize, int staleThresholdMinutes) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive, got: " + pageSize);
        }
        if (staleThresholdMinutes <= 0) {
            throw new IllegalArgumentException("Stale threshold minutes must be positive, got: " + staleThresholdMinutes);
        }
    }

    @Scheduled(fixedRate = MONITORING_INTERVAL_MS)
    public void collectAndQueueStaleAppliances() {
        log.info("Starting appliance collection cycle");
        Instant startTime = Instant.now();
        
        try {
            fetchAndQueueStaleAppliances();
            
            Duration elapsed = Duration.between(startTime, Instant.now());
            log.info("Collection cycle completed: {} total appliances, {} stale (took {}ms)", 
                    totalAppliancesProcessed, staleAppliancesFound, elapsed.toMillis());
            
            // Emit metrics for monitoring
            log.info("METRIC: appliances.total.count={}", totalAppliancesProcessed);
            log.info("METRIC: appliances.stale.count={}", staleAppliancesFound);
            log.info("METRIC: collection.duration.ms={}", elapsed.toMillis());
            
        } catch (Exception e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            log.error("Collection cycle failed after {}ms", elapsed.toMillis(), e);
            log.info("METRIC: collection.failures.count=1");
        }
    }

    private void fetchAndQueueStaleAppliances() {
        totalAppliancesProcessed = 0;
        staleAppliancesFound = 0;
        String after = null;
        int pageCount = 0;
        Instant now = Instant.now();
        
        do {
            try {
                AppliancePageResponse response = apiClient.getAppliances(after, pageSize);
                
                if (response != null && response.getData() != null) {
                    List<Appliance> pageAppliances = response.getData();
                    totalAppliancesProcessed += pageAppliances.size();
                    pageCount++;
                    
                    // Filter and process stale appliances immediately
                    for (Appliance appliance : pageAppliances) {
                        if (needsRemediation(appliance, now)) {
                            remediationProcessor.processAppliance(appliance);
                            staleAppliancesFound++;
                        }
                    }
                    
                    if (response.getPageInfo() != null) {
                        after = response.getPageInfo().getEndCursor();
                        if (!response.getPageInfo().isHasNextPage()) {
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    log.warn("Received null or empty response from API");
                    break;
                }
                
            } catch (Exception e) {
                log.error("Failed to fetch appliances page {} (after: {})", pageCount + 1, after, e);
                throw e;
            }
            
        } while (after != null);
        
        log.info("Processed {} appliances across {} pages", totalAppliancesProcessed, pageCount);
    }



    boolean needsRemediation(Appliance appliance, Instant now) {
        // Must be LIVE status
        if (!LIVE_STATUS.equals(appliance.getOpStatus())) {
            return false;
        }
        
        // Check if lastHeardFromOn is null or stale
        String lastHeardFromOn = appliance.getLastHeardFromOn();
        if (lastHeardFromOn == null) {
            log.debug("Appliance {} needs remediation: lastHeardFromOn is null", appliance.getId());
            return true;
        }
        
        try {
            Instant lastContact = Instant.parse(lastHeardFromOn);
            Duration timeSinceContact = Duration.between(lastContact, now);
            
            if (timeSinceContact.compareTo(staleThreshold) > 0) {
                log.debug("Appliance {} needs remediation: last contact {} min ago",
                        appliance.getId(), timeSinceContact.toMinutes());
                return true;
            }
            
        } catch (Exception e) {
            log.warn("Failed to parse lastHeardFromOn '{}' for appliance {}, treating as stale", 
                    lastHeardFromOn, appliance.getId());
            return true;
        }
        
        return false;
    }
}
