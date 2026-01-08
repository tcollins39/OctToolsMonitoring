package com.octtools.appliance.config;

public final class ConfigProperties {
    
    // API Configuration
    public static final String API_BASE_URL = "${appliance.api.base-url}";
    public static final String API_AUTH_HEADER = "${appliance.api.auth-header}";
    public static final String API_PAGE_SIZE = "${appliance.api.page-size}";
    public static final String API_TIMEOUT_SECONDS = "${appliance.api.timeout-seconds}";
    
    // Processing Configuration
    public static final String PROCESSING_ACTOR_EMAIL = "${appliance.processing.actor-email}";
    public static final String PROCESSING_STALE_THRESHOLD_MINUTES = "${appliance.processing.stale-threshold-minutes}";
    public static final String PROCESSING_THREAD_POOL_SIZE = "${appliance.processing.thread-pool-size}";
    
    // Queue Configuration
    public static final String QUEUE_MAX_SIZE = "${appliance.queue.max-size}";
    
    private ConfigProperties() {
        // Utility class - prevent instantiation
    }
}
