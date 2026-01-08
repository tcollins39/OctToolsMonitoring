package com.octtools.appliance.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static com.octtools.appliance.support.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationTest {

    @Test
    void testOperationBuilder() {
        Instant now = Instant.now();
        
        Operation operation = Operation.builder()
            .applianceId(TEST_APPLIANCE_ID)
            .operationType(DRAIN_OPERATION_TYPE)
            .processedAt(now)
            .drainId(DRAIN_ID)
            .build();
        
        assertEquals(TEST_APPLIANCE_ID, operation.getApplianceId());
        assertEquals(DRAIN_OPERATION_TYPE, operation.getOperationType());
        assertEquals(now, operation.getProcessedAt());
        assertEquals(DRAIN_ID, operation.getDrainId());
    }

    @Test
    void testOperationNoArgsConstructor() {
        Operation operation = new Operation();
        assertNotNull(operation);
    }
}
