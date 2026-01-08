package com.octtools.appliance.model;

import org.junit.jupiter.api.Test;

import static com.octtools.appliance.support.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplianceTest {

    @Test
    void testApplianceCreation() {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, TIMESTAMP_1);
        
        assertEquals(TEST_APPLIANCE_ID, appliance.getId());
        assertEquals(LIVE_STATUS, appliance.getOpStatus());
        assertEquals(TIMESTAMP_1, appliance.getLastHeardFromOn());
    }
    
    @Test
    void testApplianceWithNullTimestamp() {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        
        assertEquals(TEST_APPLIANCE_ID, appliance.getId());
        assertEquals(LIVE_STATUS, appliance.getOpStatus());
        assertNull(appliance.getLastHeardFromOn());
    }
    
    @Test
    void testApplianceEquality() {
        Appliance appliance1 = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, TIMESTAMP_2);
        Appliance appliance2 = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, TIMESTAMP_2);
        
        assertEquals(appliance1, appliance2);
        assertEquals(appliance1.hashCode(), appliance2.hashCode());
    }
    
    @Test
    void testApplianceInequality() {
        Appliance appliance1 = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, TIMESTAMP_2);
        Appliance appliance2 = new Appliance(TEST_APPLIANCE_ID_2, LIVE_STATUS, TIMESTAMP_2);
        
        assertNotEquals(appliance1, appliance2);
    }
    
    @Test
    void testApplianceToString() {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, TIMESTAMP_2);
        String toString = appliance.toString();
        
        assertTrue(toString.contains(TEST_APPLIANCE_ID));
        assertTrue(toString.contains(LIVE_STATUS));
        assertTrue(toString.contains(TIMESTAMP_2));
    }
    
    @Test
    void testDifferentOpStatuses() {
        Appliance liveAppliance = new Appliance("id1", LIVE_STATUS, TIMESTAMP_2);
        Appliance offlineAppliance = new Appliance("id2", OFFLINE_STATUS, TIMESTAMP_2);
        Appliance drainingAppliance = new Appliance("id3", DRAINING_STATUS, TIMESTAMP_2);
        
        assertEquals(LIVE_STATUS, liveAppliance.getOpStatus());
        assertEquals(OFFLINE_STATUS, offlineAppliance.getOpStatus());
        assertEquals(DRAINING_STATUS, drainingAppliance.getOpStatus());
    }
}
