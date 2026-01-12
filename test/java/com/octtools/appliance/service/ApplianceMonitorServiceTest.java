package com.octtools.appliance.service;

import com.octtools.appliance.client.ApplianceApiClient;
import com.octtools.appliance.model.Appliance;
import com.octtools.appliance.model.api.AppliancePageResponse;
import com.octtools.appliance.model.api.PageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.octtools.appliance.support.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplianceMonitorServiceTest {

    @Mock
    private ApplianceApiClient apiClient;
    
    @Mock
    private RemediationProcessor remediationProcessor;
    
    private ApplianceMonitorService service;
    
    @BeforeEach
    void setUp() {
        service = new ApplianceMonitorService(apiClient, remediationProcessor, 10, 10); // pageSize=10, threshold=10min
    }
    
    @Test
    void needsRemediation_liveAndStale_returnsTrue() {
        // Appliance that's LIVE but hasn't been heard from in 15 minutes
        Instant staleTime = Instant.now().minusSeconds(15 * 60);
        String staleTimestamp = DateTimeFormatter.ISO_INSTANT.format(staleTime);
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, staleTimestamp);
        
        boolean result = service.needsRemediation(appliance, Instant.now());
        
        assertTrue(result, "LIVE appliance with >10min old timestamp should need remediation");
    }
    
    @Test
    void needsRemediation_liveButNullTimestamp_returnsTrue() {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        
        boolean result = service.needsRemediation(appliance, Instant.now());
        
        assertTrue(result, "LIVE appliance with null timestamp should need remediation");
    }
    
    @Test
    void needsRemediation_notLive_returnsFalse() {
        Instant staleTime = Instant.now().minusSeconds(15 * 60);
        String staleTimestamp = DateTimeFormatter.ISO_INSTANT.format(staleTime);
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, OFFLINE_STATUS, staleTimestamp);
        
        boolean result = service.needsRemediation(appliance, Instant.now());
        
        assertFalse(result, "Non-LIVE appliance should not need remediation regardless of timestamp");
    }
    
    @Test
    void needsRemediation_liveButRecentContact_returnsFalse() {
        // Appliance contacted 5 minutes ago (within 10 minute threshold)
        Instant recentTime = Instant.now().minusSeconds(5 * 60);
        String recentTimestamp = DateTimeFormatter.ISO_INSTANT.format(recentTime);
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, recentTimestamp);
        
        boolean result = service.needsRemediation(appliance, Instant.now());
        
        assertFalse(result, "LIVE appliance with recent contact should not need remediation");
    }
    
    @Test
    void needsRemediation_exactThreshold_returnsFalse() {
        // Appliance contacted exactly 10 minutes ago (at threshold)
        Instant now = Instant.now();
        Instant thresholdTime = now.minusSeconds(10 * 60);
        String thresholdTimestamp = DateTimeFormatter.ISO_INSTANT.format(thresholdTime);
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, thresholdTimestamp);
        
        boolean result = service.needsRemediation(appliance, now);
        
        assertFalse(result, "Appliance at exact threshold should not need remediation");
    }
    
    @Test
    void needsRemediation_invalidTimestamp_returnsTrue() {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, "invalid-timestamp");
        
        boolean result = service.needsRemediation(appliance, Instant.now());
        
        assertTrue(result, "LIVE appliance with invalid timestamp should need remediation");
    }
    
    @Test
    void collectAndQueueStaleAppliances_callsApiClient() {
        AppliancePageResponse response = new AppliancePageResponse(
            List.of(new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null)),
            new PageInfo(1, false, null)
        );
        
        when(apiClient.getAppliances(null, 10)).thenReturn(response);
        
        service.collectAndQueueStaleAppliances();
        
        verify(apiClient).getAppliances(null, 10);
        verify(remediationProcessor).processAppliance(any());
    }

    @Test
    void collectAndQueueStaleAppliances_handlesPagination() {
        // First page with hasNextPage=true
        AppliancePageResponse page1 = new AppliancePageResponse(
            List.of(new Appliance("app1", LIVE_STATUS, null)),
            new PageInfo(2, true, "cursor-page2")
        );
        
        // Second page with hasNextPage=false
        AppliancePageResponse page2 = new AppliancePageResponse(
            List.of(new Appliance("app2", LIVE_STATUS, null)),
            new PageInfo(2, false, null)
        );
        
        when(apiClient.getAppliances(null, 10)).thenReturn(page1);
        when(apiClient.getAppliances("cursor-page2", 10)).thenReturn(page2);
        
        service.collectAndQueueStaleAppliances();
        
        verify(apiClient).getAppliances(null, 10);
        verify(apiClient).getAppliances("cursor-page2", 10);
        verify(remediationProcessor, times(2)).processAppliance(any());
    }
}
