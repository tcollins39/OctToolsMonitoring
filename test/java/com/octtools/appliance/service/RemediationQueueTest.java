package com.octtools.appliance.service;

import com.octtools.appliance.model.Appliance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.octtools.appliance.support.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class RemediationQueueTest {

    private RemediationQueue queue;
    
    @BeforeEach
    void setUp() {
        queue = new RemediationQueue(3); // Small capacity for testing
    }
    
    @Test
    void addAppliances_deduplicatesByApplianceId() {
        Appliance appliance1 = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, TIMESTAMP_1);
        Appliance appliance2 = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, TIMESTAMP_2); // Same ID, different timestamp
        
        queue.addAppliances(List.of(appliance1));
        queue.addAppliances(List.of(appliance2));
        
        Appliance result = queue.pollOne();
        assertNotNull(result, "Should have one appliance");
        assertEquals(TEST_APPLIANCE_ID, result.getId());
        assertNull(queue.pollOne(), "Should not have duplicate");
    }
    
    @Test
    void addAppliances_respectsCapacity() {
        List<Appliance> appliances = List.of(
            new Appliance("id1", LIVE_STATUS, TIMESTAMP_1),
            new Appliance("id2", LIVE_STATUS, TIMESTAMP_1),
            new Appliance("id3", LIVE_STATUS, TIMESTAMP_1),
            new Appliance("id4", LIVE_STATUS, TIMESTAMP_1) // This should be skipped
        );
        
        queue.addAppliances(appliances);
        
        // Poll all items to count them
        int count = 0;
        while (queue.pollOne() != null) {
            count++;
        }
        assertEquals(3, count, "Should respect queue capacity and skip overflow");
    }
    
    @Test
    void addAppliances_skipsNewItemsWhenFull() {
        // Fill queue to capacity
        queue.addAppliances(List.of(
            new Appliance("existing1", LIVE_STATUS, TIMESTAMP_1),
            new Appliance("existing2", LIVE_STATUS, TIMESTAMP_1),
            new Appliance("existing3", LIVE_STATUS, TIMESTAMP_1)
        ));
        
        // Try to add more - should be skipped
        queue.addAppliances(List.of(
            new Appliance("new1", LIVE_STATUS, TIMESTAMP_1)
        ));
        
        // Poll all items and check none are "new1"
        List<String> ids = new ArrayList<>();
        Appliance appliance;
        while ((appliance = queue.pollOne()) != null) {
            ids.add(appliance.getId());
        }
        
        assertEquals(3, ids.size());
        assertFalse(ids.contains("new1"), "New items should be skipped when queue is full");
    }
    
    @Test
    void pollOne_emptiesQueue() {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, TIMESTAMP_1);
        queue.addAppliances(List.of(appliance));
        
        Appliance result1 = queue.pollOne();
        Appliance result2 = queue.pollOne();
        
        assertNotNull(result1, "First poll should return the appliance");
        assertNull(result2, "Second poll should return null");
    }
    
    @Test
    void pollOne_returnsNullWhenEmpty() {
        Appliance result = queue.pollOne();
        
        assertNull(result);
    }
    
    @Test
    void addAppliances_handlesEmptyList() {
        queue.addAppliances(List.of());
        
        Appliance result = queue.pollOne();
        assertNull(result);
    }
    
    @Test
    void addAppliances_handlesNullTimestamp() {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        
        queue.addAppliances(List.of(appliance));
        
        Appliance result = queue.pollOne();
        assertNotNull(result);
        assertNull(result.getLastHeardFromOn());
    }
}
