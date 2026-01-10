package com.octtools.appliance.support;

public final class TestConstants {
    
    // Appliance Test Data
    public static final String TEST_APPLIANCE_ID = "appliance-123";
    public static final String TEST_APPLIANCE_ID_2 = "appliance-456";
    public static final String LIVE_STATUS = "LIVE";
    public static final String OFFLINE_STATUS = "OFFLINE";
    public static final String DRAINING_STATUS = "DRAINING";
    public static final String TIMESTAMP_1 = "2023-04-19T10:55:21.658540-07:00";
    public static final String TIMESTAMP_2 = "2023-01-01T10:00:00Z";
    
    // Email Addresses
    public static final String TEST_EMAIL = "test@example.com";
    public static final String ENGINEER_EMAIL = "engineer@company.com";
    public static final String SYSTEM_EMAIL = "system@company.com";
    
    // Request/Response Data
    public static final String DRAIN_REASON = "System maintenance";
    public static final String REMEDIATE_REASON = "Automated remediation";
    public static final String DRAIN_ID = "f502afdd-33cb-4f49-8fe5-d3201d3f9c43";
    public static final String REMEDIATION_ID = "rem-456-def-789";
    public static final String ESTIMATED_TIME = "PT8H6M12.345S";
    public static final String REMEDIATION_RESULT = "SUCCESS";
    
    // Pagination Data
    public static final int TOTAL_COUNT = 10000;
    public static final String END_CURSOR = "base64-encoded-cursor";
    
    // Operation Data
    public static final Long OPERATION_ID = 9999L;
    public static final String DRAIN_OPERATION_TYPE = "DRAIN";
    public static final String REMEDIATE_OPERATION_TYPE = "REMEDIATE";
    
    private TestConstants() {
        // Utility class - prevent instantiation
    }
}
