package com.octtools.appliance.client;

import com.octtools.appliance.model.api.AppliancePageResponse;
import com.octtools.appliance.model.api.DrainRequest;
import com.octtools.appliance.model.api.DrainResponse;
import com.octtools.appliance.model.api.RemediateRequest;
import com.octtools.appliance.model.api.RemediateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Function;

import static com.octtools.appliance.support.TestConstants.TEST_APPLIANCE_ID;
import static com.octtools.appliance.support.TestConstants.TEST_EMAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplianceApiClientTest {

    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec getRequestUriSpec;
    @Mock
    private WebClient.RequestBodyUriSpec postRequestUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ApplianceApiClient client;

    @BeforeEach
    void setUp() {
        client = new ApplianceApiClient(webClient, 30, TEST_EMAIL);
    }

    @Test
    void constructor_validatesInputs() {
        assertThrows(IllegalArgumentException.class, 
            () -> new ApplianceApiClient(null, 30, TEST_EMAIL));
            
        assertThrows(IllegalArgumentException.class,
            () -> new ApplianceApiClient(webClient, 0, TEST_EMAIL));
            
        assertThrows(IllegalArgumentException.class,
            () -> new ApplianceApiClient(webClient, 30, null));
    }

    @Test
    void constructor_createsValidClient() {
        assertNotNull(client);
    }

    @Test
    void drainAppliance_callsCorrectEndpointWithCorrectPayload() {
        DrainResponse expectedResponse = new DrainResponse();
        
        when(webClient.post()).thenReturn(postRequestUriSpec);
        when(postRequestUriSpec.uri(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(DrainResponse.class)).thenReturn(Mono.just(expectedResponse));
        
        DrainResponse result = client.drainAppliance(TEST_APPLIANCE_ID);
        
        // Verify correct endpoint was called
        verify(postRequestUriSpec).uri("/api/1.0/appliances/{id}/drain", TEST_APPLIANCE_ID);
        
        // Verify correct payload was sent
        ArgumentCaptor<DrainRequest> requestCaptor = ArgumentCaptor.forClass(DrainRequest.class);
        verify(requestBodySpec).bodyValue(requestCaptor.capture());
        
        DrainRequest capturedRequest = requestCaptor.getValue();
        assertEquals("Appliance " + TEST_APPLIANCE_ID + " detected as stale - automated drain", capturedRequest.getReason());
        assertEquals(TEST_EMAIL, capturedRequest.getActor());
        
        assertNotNull(result);
        assertEquals(expectedResponse, result);
    }

    @Test
    void remediateAppliance_callsCorrectEndpointWithCorrectPayload() {
        RemediateResponse expectedResponse = new RemediateResponse();
        
        when(webClient.post()).thenReturn(postRequestUriSpec);
        when(postRequestUriSpec.uri(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(RemediateResponse.class)).thenReturn(Mono.just(expectedResponse));
        
        RemediateResponse result = client.remediateAppliance(TEST_APPLIANCE_ID);
        
        // Verify correct endpoint was called
        verify(postRequestUriSpec).uri("/api/1.0/appliances/{id}/remediate", TEST_APPLIANCE_ID);
        
        // Verify correct payload was sent
        ArgumentCaptor<RemediateRequest> requestCaptor = ArgumentCaptor.forClass(RemediateRequest.class);
        verify(requestBodySpec).bodyValue(requestCaptor.capture());
        
        RemediateRequest capturedRequest = requestCaptor.getValue();
        assertEquals("Appliance " + TEST_APPLIANCE_ID + " remediation after drain", capturedRequest.getReason());
        assertEquals(TEST_EMAIL, capturedRequest.getActor());
        
        assertNotNull(result);
        assertEquals(expectedResponse, result);
    }

    @Test
    void getAppliances_withCursor_buildsCorrectUri() {
        AppliancePageResponse expectedResponse = new AppliancePageResponse();
        
        when(webClient.get()).thenReturn(getRequestUriSpec);
        when(getRequestUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(AppliancePageResponse.class)).thenReturn(Mono.just(expectedResponse));
        
        client.getAppliances("cursor-123", 25);
        
        ArgumentCaptor<Function<UriBuilder, URI>> uriBuilderCaptor = ArgumentCaptor.forClass(Function.class);
        verify(getRequestUriSpec).uri(uriBuilderCaptor.capture());
        
        // Test the URI building function
        UriBuilder mockUriBuilder = mock(UriBuilder.class);
        when(mockUriBuilder.path("/api/1.0/appliances")).thenReturn(mockUriBuilder);
        when(mockUriBuilder.queryParam("first", 25)).thenReturn(mockUriBuilder);
        when(mockUriBuilder.queryParam("after", "cursor-123")).thenReturn(mockUriBuilder);
        when(mockUriBuilder.build()).thenReturn(URI.create("http://test.com"));
        
        uriBuilderCaptor.getValue().apply(mockUriBuilder);
        
        verify(mockUriBuilder).path("/api/1.0/appliances");
        verify(mockUriBuilder).queryParam("first", 25);
        verify(mockUriBuilder).queryParam("after", (Object) "cursor-123");
    }

    @Test
    void getAppliances_withoutCursor_buildsCorrectUri() {
        AppliancePageResponse expectedResponse = new AppliancePageResponse();
        
        when(webClient.get()).thenReturn(getRequestUriSpec);
        when(getRequestUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(AppliancePageResponse.class)).thenReturn(Mono.just(expectedResponse));
        
        client.getAppliances(null, 25);
        
        ArgumentCaptor<Function<UriBuilder, URI>> uriBuilderCaptor = ArgumentCaptor.forClass(Function.class);
        verify(getRequestUriSpec).uri(uriBuilderCaptor.capture());
        
        // Test the URI building function
        UriBuilder mockUriBuilder = mock(UriBuilder.class);
        when(mockUriBuilder.path("/api/1.0/appliances")).thenReturn(mockUriBuilder);
        when(mockUriBuilder.queryParam("first", 25)).thenReturn(mockUriBuilder);
        when(mockUriBuilder.build()).thenReturn(URI.create("http://test.com"));
        
        uriBuilderCaptor.getValue().apply(mockUriBuilder);
        
        verify(mockUriBuilder).path("/api/1.0/appliances");
        verify(mockUriBuilder).queryParam("first", 25);
        verify(mockUriBuilder, never()).queryParam(eq("after"), (Object) any());
    }
}
