package com.octtools.appliance.service;

import com.octtools.appliance.client.ApplianceApiClient;
import com.octtools.appliance.model.Appliance;
import com.octtools.appliance.model.Operation;
import com.octtools.appliance.model.api.DrainResponse;
import com.octtools.appliance.model.api.RemediateResponse;
import com.octtools.appliance.repository.OperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.octtools.appliance.support.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemediationProcessorTest {

    @Mock
    private ApplianceApiClient apiClient;
    
    @Mock
    private OperationRepository operationRepository;
    
    private RemediationProcessor processor;
    
    @BeforeEach
    void setUp() {
        processor = new RemediationProcessor(apiClient, operationRepository, 2);
    }
    
    @Test
    void constructor_validatesInputs() {
        assertThrows(IllegalArgumentException.class, 
            () -> new RemediationProcessor(apiClient, operationRepository, 0));
    }

    @Test
    void processAppliance_successfullyProcessesAppliance() throws InterruptedException {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        when(apiClient.drainAppliance(anyString())).thenReturn(new DrainResponse(DRAIN_ID, ESTIMATED_TIME));
        when(apiClient.remediateAppliance(anyString())).thenReturn(new RemediateResponse(REMEDIATION_ID, REMEDIATION_RESULT));
        
        processor.processAppliance(appliance);
        
        // Allow async processing to complete
        Thread.sleep(200);
        
        verify(apiClient).drainAppliance(TEST_APPLIANCE_ID);
        verify(apiClient).remediateAppliance(TEST_APPLIANCE_ID);
        
        // Verify correct Operation objects are saved
        ArgumentCaptor<Operation> operationCaptor = ArgumentCaptor.forClass(Operation.class);
        verify(operationRepository, times(2)).save(operationCaptor.capture());
        
        List<Operation> savedOperations = operationCaptor.getAllValues();
        
        // First operation should be DRAIN
        Operation drainOp = savedOperations.get(0);
        assertEquals(TEST_APPLIANCE_ID, drainOp.getApplianceId());
        assertEquals(DRAIN_OPERATION_TYPE, drainOp.getOperationType());
        assertEquals(DRAIN_ID, drainOp.getDrainId());
        assertEquals(ESTIMATED_TIME, drainOp.getEstimatedTimeToDrain());
        
        // Second operation should be REMEDIATE
        Operation remediateOp = savedOperations.get(1);
        assertEquals(TEST_APPLIANCE_ID, remediateOp.getApplianceId());
        assertEquals(REMEDIATE_OPERATION_TYPE, remediateOp.getOperationType());
        assertEquals(REMEDIATION_ID, remediateOp.getRemediationId());
        assertEquals(REMEDIATION_RESULT, remediateOp.getRemediationResult());
    }

    @Test
    void processAppliance_drainFailsAfterRetries_noRemediateCall() throws InterruptedException {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        when(apiClient.drainAppliance(anyString())).thenThrow(new RuntimeException("Drain failed after retries"));
        
        processor.processAppliance(appliance);
        
        // Allow async processing to complete
        Thread.sleep(200);
        
        verify(apiClient).drainAppliance(TEST_APPLIANCE_ID);
        verify(apiClient, never()).remediateAppliance(anyString());
        verify(operationRepository, never()).save(any());
    }

    @Test
    void processAppliance_drainSucceedsRemediateFails_onlyDrainOperationSaved() throws InterruptedException {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        when(apiClient.drainAppliance(anyString())).thenReturn(new DrainResponse(DRAIN_ID, ESTIMATED_TIME));
        when(apiClient.remediateAppliance(anyString())).thenThrow(new RuntimeException("Remediate failed after retries"));
        
        processor.processAppliance(appliance);
        
        // Allow async processing to complete
        Thread.sleep(200);
        
        verify(apiClient).drainAppliance(TEST_APPLIANCE_ID);
        verify(apiClient).remediateAppliance(TEST_APPLIANCE_ID);
        
        // Only drain operation should be saved
        ArgumentCaptor<Operation> operationCaptor = ArgumentCaptor.forClass(Operation.class);
        verify(operationRepository, times(1)).save(operationCaptor.capture());
        
        Operation drainOp = operationCaptor.getValue();
        assertEquals(TEST_APPLIANCE_ID, drainOp.getApplianceId());
        assertEquals(DRAIN_OPERATION_TYPE, drainOp.getOperationType());
        assertEquals(DRAIN_ID, drainOp.getDrainId());
    }
}
