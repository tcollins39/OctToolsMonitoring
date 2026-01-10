#!/bin/bash

# Demo Script for OctTools Appliance Monitoring Service
# This script demonstrates how to interact with the running service

BASE_URL="http://localhost:8080"
API_URL="$BASE_URL/api/v1"

echo "=== OctTools Appliance Monitoring Service Demo ==="
echo "Make sure the application is running on port 8080"
echo

# Function to check if service is running
check_service() {
    echo "1. Checking if service is running..."
    if curl -s "$BASE_URL/actuator/health" | grep -q '"status":"UP"'; then
        echo "Service is UP and running"
        return 0
    else
        echo "Service is not running. Please start it with:"
        echo "   mvn spring-boot:run"
        echo "   OR"
        echo "   java -jar target/appliance-monitor-1.0.0.jar"
        exit 1
    fi
}

# Function to get all operations
get_all_operations() {
    echo
    echo "2. Getting all operations..."
    response=$(curl -s "$API_URL/operations")
    total_elements=$(echo "$response" | jq -r '.totalElements' 2>/dev/null || echo "0")
    
    if [ "$total_elements" = "0" ]; then
        echo "No operations found yet (service may still be starting up)"
        echo "   Operations will appear after the first monitoring cycle (up to 5 minutes)"
    else
        echo "Found $total_elements operations:"
        echo "$response" | jq '.content[] | {id, applianceId, operationType, processedAt}' 2>/dev/null || echo "$response"
    fi
}

# Function to get operations with pagination
get_operations_paginated() {
    echo
    echo "3. Getting operations with pagination (page size 5)..."
    response=$(curl -s "$API_URL/operations?size=5&page=0")
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
}

# Function to get operations for specific appliance (if any exist)
get_operations_by_appliance() {
    echo
    echo "4. Getting operations for specific appliance..."
    
    # Try to get the first appliance ID from existing operations
    appliance_id=$(curl -s "$API_URL/operations?size=1" | jq -r '.content[0].applianceId' 2>/dev/null)
    
    if [ "$appliance_id" != "null" ] && [ -n "$appliance_id" ]; then
        echo "Found appliance: $appliance_id"
        echo "   Getting all operations for this appliance:"
        response=$(curl -s "$API_URL/operations?applianceId=$appliance_id")
        echo "$response" | jq '.content[] | {id, operationType, processedAt, drainId, remediationId}' 2>/dev/null || echo "$response"
    else
        echo "No appliances found yet - demonstrating with example ID:"
        echo "   curl \"$API_URL/operations?applianceId=appliance-123\""
        curl -s "$API_URL/operations?applianceId=appliance-123" | jq '.' 2>/dev/null || curl -s "$API_URL/operations?applianceId=appliance-123"
    fi
}

# Function to show service status and metrics
show_service_info() {
    echo
    echo "5. Service Information:"
    echo "   Health Endpoint: $BASE_URL/actuator/health"
    echo "   Operations API:  $API_URL/operations"
    echo "   Monitoring:      Every 5 minutes"
    echo "   Processing:      Immediate async processing"
    echo
    echo "Current Status:"
    health_status=$(curl -s "$BASE_URL/actuator/health" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
    echo "   Health: $health_status"
    
    total_ops=$(curl -s "$API_URL/operations" | jq -r '.totalElements' 2>/dev/null || echo "0")
    echo "   Total Operations: $total_ops"
}

# Function to demonstrate error handling
demonstrate_error_handling() {
    echo
    echo "6. Demonstrating error handling..."
    echo "   Testing invalid endpoint:"
    curl -s "$API_URL/invalid-endpoint" | head -c 200
    echo
}

# Main execution
main() {
    check_service
    get_all_operations
    get_operations_paginated
    get_operations_by_appliance
    show_service_info
    demonstrate_error_handling
    
    echo
    echo "=== Demo Complete ==="
    echo
    echo "Tips:"
    echo "   - Operations appear after stale appliances are detected (every 5 minutes)"
    echo "   - Each appliance gets 2 operations: DRAIN followed by REMEDIATE"
    echo "   - Check application logs to see real-time processing"
    echo "   - Use 'curl $API_URL/operations' to check for new operations"
    echo
    echo "Troubleshooting:"
    echo "   - If no operations appear, the external API may be unavailable"
    echo "   - Check application logs for DNS resolution errors"
    echo "   - The service will continue running and retry automatically"
}

# Run the demo
main
