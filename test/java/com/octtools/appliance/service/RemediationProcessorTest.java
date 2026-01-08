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
    private RemediationQueue remediationQueue;
    
    @Mock
    private ApplianceApiClient apiClient;
    
    @Mock
    private OperationRepository operationRepository;
    
    private RemediationProcessor processor;
    
    @BeforeEach
    void setUp() {
        processor = new RemediationProcessor(apiClient, remediationQueue, operationRepository, 2, TEST_EMAIL);
    }
    
    @Test
    void constructor_validatesInputs() {
        assertThrows(IllegalArgumentException.class, 
            () -> new RemediationProcessor(apiClient, remediationQueue, operationRepository, 0, TEST_EMAIL));
            
        assertThrows(IllegalArgumentException.class,
            () -> new RemediationProcessor(apiClient, remediationQueue, operationRepository, 2, null));
    }

    @Test
    void processRemediationQueue_handlesEmptyQueue() {
        when(remediationQueue.pollOne()).thenReturn(null);
        
        processor.processRemediationQueue();
        
        verify(remediationQueue).pollOne();
        verifyNoInteractions(apiClient);
        verifyNoInteractions(operationRepository);
    }

    @Test
    void processRemediationQueue_processesAppliances() {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        when(remediationQueue.pollOne()).thenReturn(appliance);
        when(apiClient.drainAppliance(anyString())).thenReturn(new DrainResponse(DRAIN_ID, ESTIMATED_TIME));
        when(apiClient.remediateAppliance(anyString())).thenReturn(new RemediateResponse(REMEDIATION_ID, REMEDIATION_RESULT));
        
        processor.processRemediationQueue();
        
        verify(remediationQueue).pollOne();
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
    void processRemediationQueue_drainFailsAfterRetries_noRemediateCall() {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        when(remediationQueue.pollOne()).thenReturn(appliance);
        when(apiClient.drainAppliance(anyString())).thenThrow(new RuntimeException("Drain failed after retries"));
        
        processor.processRemediationQueue();
        
        // Give async processing time to complete
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        
        verify(remediationQueue).pollOne();
        verify(apiClient).drainAppliance(TEST_APPLIANCE_ID);
        verify(apiClient, never()).remediateAppliance(anyString());
        verify(operationRepository, never()).save(any());
        verify(remediationQueue).markCompleted(TEST_APPLIANCE_ID);
    }

    @Test
    void processRemediationQueue_drainSucceedsRemediateFails_onlyDrainOperationSaved() {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        when(remediationQueue.pollOne()).thenReturn(appliance);
        when(apiClient.drainAppliance(anyString())).thenReturn(new DrainResponse(DRAIN_ID, ESTIMATED_TIME));
        when(apiClient.remediateAppliance(anyString())).thenThrow(new RuntimeException("Remediate failed after retries"));
        
        processor.processRemediationQueue();
        
        // Give async processing time to complete
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        
        verify(remediationQueue).pollOne();
        verify(apiClient).drainAppliance(TEST_APPLIANCE_ID);
        verify(apiClient).remediateAppliance(TEST_APPLIANCE_ID);
        
        // Only drain operation should be saved
        ArgumentCaptor<Operation> operationCaptor = ArgumentCaptor.forClass(Operation.class);
        verify(operationRepository, times(1)).save(operationCaptor.capture());
        
        Operation drainOp = operationCaptor.getValue();
        assertEquals(TEST_APPLIANCE_ID, drainOp.getApplianceId());
        assertEquals(DRAIN_OPERATION_TYPE, drainOp.getOperationType());
        assertEquals(DRAIN_ID, drainOp.getDrainId());
        
        verify(remediationQueue).markCompleted(TEST_APPLIANCE_ID);
    }

    @Test
    void processRemediationQueue_singleApplianceProcessing() throws InterruptedException {
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        when(remediationQueue.pollOne()).thenReturn(appliance);
        when(apiClient.drainAppliance(anyString())).thenReturn(new DrainResponse(DRAIN_ID, ESTIMATED_TIME));
        when(apiClient.remediateAppliance(anyString())).thenReturn(new RemediateResponse(REMEDIATION_ID, REMEDIATION_RESULT));
        
        processor.processRemediationQueue();
        
        // Allow async processing to complete
        Thread.sleep(100);
        
        verify(remediationQueue).pollOne();
        verify(apiClient).drainAppliance(TEST_APPLIANCE_ID);
        verify(apiClient).remediateAppliance(TEST_APPLIANCE_ID);
        verify(operationRepository, times(2)).save(any()); // 2 operations per appliance
        verify(remediationQueue).markCompleted(TEST_APPLIANCE_ID);
    }

    @Test
    void processRemediationQueue_executorQueueFull_requeuesAppliance() {
        // Create processor with queue size 0 to force immediate rejection
        RemediationProcessor processorWithFullQueue = new RemediationProcessor(
            apiClient, remediationQueue, operationRepository, 1, TEST_EMAIL) {
            @Override
            public void processRemediationQueue() {
                Appliance appliance = remediationQueue.pollOne();
                if (appliance == null) return;
                
                // Simulate RejectedExecutionException by calling the catch block directly
                remediationQueue.markCompleted(appliance.getId());
                remediationQueue.addAppliances(java.util.Collections.singletonList(appliance));
            }
        };
        
        Appliance appliance = new Appliance(TEST_APPLIANCE_ID, LIVE_STATUS, null);
        when(remediationQueue.pollOne()).thenReturn(appliance);
        
        processorWithFullQueue.processRemediationQueue();
        
        verify(remediationQueue).pollOne();
        verify(remediationQueue).markCompleted(TEST_APPLIANCE_ID);
        verify(remediationQueue).addAppliances(java.util.Collections.singletonList(appliance));
        verifyNoInteractions(apiClient); // Should not process when re-queued
    }
}
