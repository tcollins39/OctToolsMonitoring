package com.octtools.appliance.model.api;

import org.junit.jupiter.api.Test;
import java.util.List;

import static com.octtools.appliance.support.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiDtoTest {

    @Test
    void testDrainRequest() {
        DrainRequest request = new DrainRequest(DRAIN_REASON, ENGINEER_EMAIL);
        
        assertEquals(DRAIN_REASON, request.getReason());
        assertEquals(ENGINEER_EMAIL, request.getActor());
    }
    
    @Test
    void testDrainResponse() {
        DrainResponse response = new DrainResponse(DRAIN_ID, ESTIMATED_TIME);
        
        assertEquals(DRAIN_ID, response.getDrainId());
        assertEquals(ESTIMATED_TIME, response.getEstimatedTimeToDrain());
    }
    
    @Test
    void testRemediateRequest() {
        RemediateRequest request = new RemediateRequest(REMEDIATE_REASON, SYSTEM_EMAIL);
        
        assertEquals(REMEDIATE_REASON, request.getReason());
        assertEquals(SYSTEM_EMAIL, request.getActor());
    }
    
    @Test
    void testRemediateResponse() {
        RemediateResponse response = new RemediateResponse(REMEDIATION_ID, REMEDIATION_RESULT);
        
        assertEquals(REMEDIATION_ID, response.getRemediationId());
        assertEquals(REMEDIATION_RESULT, response.getRemediationResult());
    }
    
    @Test
    void testPageInfo() {
        PageInfo pageInfo = new PageInfo(TOTAL_COUNT, true, END_CURSOR);
        
        assertEquals(TOTAL_COUNT, pageInfo.getTotalCount());
        assertTrue(pageInfo.isHasNextPage());
        assertEquals(END_CURSOR, pageInfo.getEndCursor());
    }
    
    @Test
    void testPageInfoWithoutNextPage() {
        PageInfo pageInfo = new PageInfo(50, false, null);
        
        assertEquals(50, pageInfo.getTotalCount());
        assertFalse(pageInfo.isHasNextPage());
        assertNull(pageInfo.getEndCursor());
    }
    
    @Test
    void testAppliancePageResponse() {
        PageInfo pageInfo = new PageInfo(1, false, null);
        AppliancePageResponse response = new AppliancePageResponse(List.of(), pageInfo);
        
        assertNotNull(response.getData());
        assertTrue(response.getData().isEmpty());
        assertEquals(pageInfo, response.getPageInfo());
    }
    
    @Test
    void testDtoNoArgsConstructors() {
        // Verify all DTOs have no-args constructors (required for JSON deserialization)
        assertDoesNotThrow(() -> new DrainRequest());
        assertDoesNotThrow(() -> new DrainResponse());
        assertDoesNotThrow(() -> new RemediateRequest());
        assertDoesNotThrow(() -> new RemediateResponse());
        assertDoesNotThrow(() -> new PageInfo());
        assertDoesNotThrow(() -> new AppliancePageResponse());
    }
}
