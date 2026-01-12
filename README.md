# OctTools Appliance Monitoring Service

A Spring Boot application that monitors appliances for stale status and automatically performs remediation operations.

## Overview

This service connects to the OctTools external API to monitor appliances. When it finds appliances that haven't been heard from 
in over 10 minutes and are still marked as "LIVE", it automatically drains and remediates them. All operations are recorded and can be queried via a REST API.

## How It Works

1. ApplianceMonitorService - Checks for stale appliances every 5 minutes
2. RemediationProcessor - Processes appliances immediately using async execution
3. For each stale appliance: drain → record operation → remediate → record operation
4. API created to view all completed operations via REST endpoints

## Key Features

- Automatic monitoring of appliance status
- Concurrent processing of multiple appliances
- Retry logic for handling API failures with exponential backoff
- Operation tracking with in-memory storage
- REST API for querying results
- Health monitoring endpoint

## Architecture

### Scheduling Strategy
The application uses a single scheduled task for monitoring with immediate async processing:

- Data Collection `@Scheduled(fixedRate = 300000)` - Runs every 5 minutes to fetch appliances and identify stale ones
- Immediate Processing - Stale appliances are submitted to a bounded executor for concurrent processing

This approach ensures consistent monitoring intervals while providing immediate processing of detected stale appliances.

### Concurrency Model
- Single-threaded data collection with per-page processing
- Multi-threaded appliance processing using a bounded ThreadPoolExecutor with100 threads and 2,500-task queue
- Immediate async processing with natural backpressure and graceful overflow handling

### Processing Architecture Design
The application implements immediate async processing with several key design decisions:

Bounded Resources:
- Thread Pool is fixed at 100 threads which is optimized to complete all processing before next collection cycle
- Executor Queue has a maximum of 2,500 tasks to provide backpressure
- Appliances are processed as pages are collected

Processing Strategy:
- Immediate submission of stale appliances to executor upon detection
- When executor queue fills, overflow appliances are skipped and retried in next monitoring cycle
- Concurrent processing of multiple appliances with proper error isolation

This architecture provides high throughput while maintaining bounded memory usage and graceful handling of overload conditions.

### External API Integration
Connects to the OctTools homework API:
- Base URL: `oct-backend-homework.us-east-1.elasticbeanstalk.com`
- Basic auth is configured in application.yml
- Cursor-based pagination for fetching all appliances
- Drain and remediate API calls with retry logic

Retry Strategy:
- Collection APIs: 4 retries with 500ms delay, 1.5x backoff (0.5s, 0.75s, 1.125s, 1.6875s delays)
- Processing APIs: 4 retries with 500ms delay, 1.1x backoff (0.5s, 0.55s, 0.605s, 0.666s delays)
  - 5 total attempts 1 initial + 4 retries provides excellent resilience against transient API failures
  - Fast initial retry (500ms) with optimized backoff multipliers for different operation types
  - Collection API failures break entire pagination cycle, so robust retry is critical
  - Operation API failures are isolated per appliance, allowing other processing to continue

## Configuration

The application uses sensible defaults with minimal required configuration:

```yaml
appliance:
  api:
    base-url: http://oct-backend-homework.us-east-1.elasticbeanstalk.com:8080
    auth-header: Basic b2N0QXBwbGljYW50OmIwZTg1YWE4LWQ2YWUtNGQzYi1iODA5LTA0ZDIwN2VkZTNmNQ==
    page-size: 100
    timeout-seconds: 5
  processing:
    actor-email: engineer@company.com
    stale-threshold-minutes: 10
    thread-pool-size: 100
```

## Running the Application

### Prerequisites
- Java 17+
- Maven 3.6+

### Quick Start
```bash
# Option 1: Run with Maven
mvn spring-boot:run

# Option 3: Build and run JAR
mvn clean package
java -jar target/appliance-monitor-1.0.0.jar
```

Application starts on `http://localhost:8080`

### Verify It's Working
```bash
# Check health
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# View operations (initially empty)
curl http://localhost:8080/api/v1/operations
# Expected: {"content":[],"totalElements":0,"empty":true,...}

# Wait 5-10 minutes for automatic processing, then check again
curl http://localhost:8080/api/v1/operations
# Expected: Array of operations as stale appliances are processed
```

Watch the application logs to see real-time monitoring and processing activity.

## Demo Script

A comprehensive demo script is provided to interact with the running service:

```bash
./demo-script.sh
```

The script demonstrates:
- Health check verification
- Getting all operations with pagination
- Filtering operations by appliance ID
- Performance metrics and system throughput
- Error handling examples
- Service status monitoring

**Prerequisites:** Ensure the application is running and `jq` is installed for JSON formatting (optional).

## API Reference

### Get All Operations
```bash
GET /api/v1/operations
```
Returns all remediation operations performed since startup with pagination.

**Query Parameters:**
- `page` (optional) - Page number (0-based, default: 0)
- `size` (optional) - Page size (default: 20, max: 100)
- `applianceId` (optional) - Filter by specific appliance ID

**Examples:**
```bash
curl http://localhost:8080/api/v1/operations
curl http://localhost:8080/api/v1/operations?page=0&size=10
```

### Get Operations by Appliance
```bash
GET /api/v1/operations?applianceId={id}
```
Returns operations for a specific appliance.

**Example:**
```bash
curl "http://localhost:8080/api/v1/operations?applianceId=appliance-abc123"
```

### Health Check
```bash
GET /actuator/health
```
Returns application health status.

**Example:**
```bash
curl http://localhost:8080/actuator/health
```

**Response:** `{"status":"UP"}` or `{"status":"DOWN"}`

### Response Format
```json
{
  "content": [
    {
      "id": 1,
      "applianceId": "appliance-abc123",
      "operationType": "DRAIN",
      "drainId": "drain-12345",
      "estimatedTimeToDrain": 300,
      "processedAt": "2026-01-08T15:30:15.123Z"
    },
    {
      "id": 2,
      "applianceId": "appliance-abc123", 
      "operationType": "REMEDIATE",
      "remediationId": "remediation-67890",
      "remediationResult": "SUCCESS",
      "processedAt": "2026-01-08T15:30:45.456Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "unsorted": true,
      "sorted": false,
      "empty": true
    }
  },
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true,
  "numberOfElements": 2,
  "empty": false
}
```

Field Descriptions:
- `content` - Array of operation objects
- `pageable` - Pagination metadata (pageNumber, pageSize, sort)
- `totalElements` - Total number of operations across all pages
- `totalPages` - Total number of pages available
- `first/last` - Boolean flags for first/last page
- `numberOfElements` - Number of operations in current page
- `empty` - Boolean flag indicating if page is empty

Operation Object Fields:
- `id` - Unique operation identifier (auto-generated)
- `applianceId` - ID of the processed appliance
- `operationType` - Either "DRAIN" or "REMEDIATE"
- `processedAt` - ISO timestamp when operation completed
- `drainId` - External API drain operation reference (DRAIN operations only)
- `estimatedTimeToDrain` - Time estimate in seconds (DRAIN operations only)
- `remediationId` - External API remediation reference (REMEDIATE operations only)
- `remediationResult` - Result status from external API (REMEDIATE operations only)

### Expected API Behavior

Initial State (Application Startup):
```bash
curl http://localhost:8080/api/v1/operations
# Returns: {"content":[],"totalElements":0,"empty":true,...}
```

After Processing (5-10 minutes after startup):
```bash
curl http://localhost:8080/api/v1/operations
# Returns: {"content":[...operations...],"totalElements":N,...}
```

Filter by Appliance:
```bash
curl "http://localhost:8080/api/v1/operations?applianceId=appliance-123"
# Returns: Only operations for that specific appliance
```

### Monitoring Processing Activity

Watch application logs to see real-time processing:
```bash
# Look for these log patterns:
# "Starting appliance collection cycle"
# "Collection cycle completed: X total appliances, Y stale"
# "Successfully processed appliance X: drain=Y, remediation=Z"
# "METRIC: appliance.processing.success.count=1"
```

## Data Storage

Important: Operation data is stored in-memory only and is lost when the application restarts.

## Testing

The application includes comprehensive unit tests covering:
- Business logic and stale detection
- API client behavior and retry logic
- Async processing and concurrency
- Service integration and error handling

Run tests with: `mvn test`

## Project Structure

```
src/main/java/com/octtools/appliance/
├── ApplianceMonitorApplication.java      # Main application
├── client/ApplianceApiClient.java        # External API client
├── config/                               # Configuration classes
├── controller/OperationController.java   # REST API endpoints
├── model/                                # Data models and DTOs
├── repository/OperationRepository.java   # Database access
└── service/                              # Business logic
    ├── ApplianceMonitorService.java      # Monitoring and detection
    └─── RemediationProcessor.java         # Processing and remediation
```

## Design Decisions

### Why Spring Boot?
- Built-in scheduling with `@Scheduled`
- REST API support with minimal configuration
- Database integration via Spring Data JPA
- Dependency injection and auto-configuration
- Industry standard for enterprise Java applications

### Why Separate Monitoring and Processing?
- Ensures consistent 5-minute monitoring regardless of processing time
- Better fault isolation - processing issues don't delay monitoring cycles
- Components can scale independently based on different needs

### Why Immediate Async Processing Over Producer/Consumer?
Initially considered a producer/consumer pattern with a queue, but the external API's dataset volatility made this approach unsuitable.
- Appliances have an extremely short time period for drain success after being discovered as stale
- Even minimal queuing delays if 100ms polling caused appliances to fail all drain attempts
- Direct async submission to bounded executor eliminates queue delays while maintaining thread safety and natural backpressure

### Why ThreadPoolExecutor vs Other Async Options?
Chose ThreadPoolExecutor over alternatives for predictable resource management:
- vs @Async: ThreadPoolExecutor provides explicit control over thread limits and queue size
- vs CompletableFuture.runAsync(): Default ForkJoinPool is unbounded and could exhaust system resources
- vs Reactive Streams: Added complexity not justified for this use case's straightforward processing needs
- Result: Direct control over concurrency with bounded resources and graceful overflow handling

### ThreadPool Sizing Rationale
Configuration: 100 threads + 2,500 queue capacity = 2,600 total system capacity

**Thread Count 100:**

The 100-thread configuration ensures processing completes within the 5-minute cycle interval, minimizing appliance aging and 404 errors. 
Thread count would be tuned in a production environment based on load testing and external API performance characteristics.
- Optimized for I/O-bound workload where threads spend minimum of 400ms waiting for API responses
- Performance testing showed 100 threads provide optimal throughput to complete processing within 5-minute cycle intervals
- Balances high throughput with resource efficiency
- Higher counts risk overwhelming the external API server with concurrent requests

**Queue Size 2,500:**
- Provides substantial burst capacity for workload spikes and system resilience
- Bounded to prevent memory exhaustion while allowing for extreme load scenarios
- When full, overflow appliances retry in next 5-minute cycle for graceful degradation
- Total system capacity of 2,600 significantly exceeds typical workload for maximum reliability

## Production Considerations

For production deployment, consider:
- Database persistence (PostgreSQL, MySQL) for operation history
- Monitoring and alerting for processing throughput and failure rates
- Configuration externalization for different environments
- Circuit breaker patterns for external API resilience
- Metrics collection for operational visibility
- Thread pool sizing based on actual load patterns and API latency
- Rate limiting to prevent overwhelming external APIs
- Distributed processing for horizontal scaling across multiple instances

## Known Limitations

- Operation history lost on application restart
- Basic error handling without circuit breaker patterns

These limitations are appropriate for the assignment scope and are noted for future enhancement.

## AI Usage

- AI was utilized to help format the README 
- Create initial project structure.
- Debugging
- SpringBoot Configuration
