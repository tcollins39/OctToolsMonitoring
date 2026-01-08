# OctTools Appliance Monitoring Service

A Spring Boot application that monitors appliances for stale status and automatically performs remediation operations.

## Overview

This service connects to the OctTools external API to monitor appliances. When it finds appliances that haven't been heard from in over 10 minutes (and are still marked as "LIVE"), it automatically drains and remediates them. All operations are recorded and can be queried via a REST API.

## How It Works

1. ApplianceMonitorService - Checks for stale appliances every 5 minutes
2. RemediationProcessor - Processes queued appliances every 100ms (high-throughput)  
3. For each stale appliance: drain → record operation → remediate → record operation
4. API created to view all completed operations via REST endpoints

## Key Features

- Automatic monitoring of appliance status
- Concurrent processing of multiple appliances
- Retry logic for handling API failures with backoff and jitter
- Operation tracking with in-memory storage
- REST API for querying results
- Health monitoring endpoint

## Architecture

### Scheduling Strategy
The application uses two separate scheduled tasks:

- Data Collection (`@Scheduled(fixedRate = 300000)`) - Runs every 5 minutes to fetch appliances and identify stale ones
- Queue Processing (`@Scheduled(fixedDelay = 100)`) - Runs every 100ms to process queued appliances one at a time

This separation ensures consistent monitoring intervals regardless of processing time and provides better fault isolation.

### Concurrency Model
- Single-threaded data collection
- Multi-threaded queue processing using a bounded ThreadPoolExecutor (10 threads, 50-task queue)
- One-at-a-time processing with natural backpressure and automatic re-queuing on overload
- In-memory queue with overflow protection (max 1000 items)
- Duplicate prevention using a processing set to avoid concurrent processing of same appliance

### Queue Architecture Design
The application implements a sophisticated queue management system with several key design decisions:

Bounded Resources:
- Input Queue: Maximum 1000 appliances to prevent memory exhaustion
- Executor Queue: Maximum 50 tasks to provide backpressure
- Thread Pool: Fixed 10 threads for predictable resource usage

Processing Strategy:
- Processes single appliances every 100ms rather than batch processing
- When executor queue fills, scheduler automatically slows down
- Rejected tasks are re-queued for later processing
- When a submission fails, external API failures trigger rediscovery on next monitoring cycle

State Management:
- Duplicate Prevention: Processing set tracks appliances currently being handled
- Proper Lifecycle: pollOne() removes from queue, markCompleted() removes from processing set
- Rejection Handling: Failed executor submissions are cleaned up and re-queued

This architecture provides high throughput while maintaining bounded memory usage and graceful handling of overload conditions.

### External API Integration
Connects to the OctTools homework API:
- Base URL: `oct-backend-homework.us-east-1.elasticbeanstalk.com`
- Authentication: Basic auth (configured in application.yml)
- Pagination: Cursor-based pagination for fetching all appliances
- Operations: Drain and remediate API calls with retry logic

## Configuration

The application uses sensible defaults with minimal required configuration:

```yaml
appliance:
  api:
    base-url: https://oct-backend-homework.us-east-1.elasticbeanstalk.com
    auth-header: Basic b2N0QXBwbGljYW50OmIwZTg1YWE4LWQ2YWUtNGQzYi1iODA5LTA0ZDIwN2VkZTNmNQ==
    page-size: 100
    timeout-seconds: 30
  processing:
    actor-email: engineer@company.com
    stale-threshold-minutes: 10
    thread-pool-size: 10
  queue:
    max-size: 1000
```

## Running the Application

### Prerequisites
- Java 17+
- Maven 3.6+

### Quick Start
```bash
# Option 1: Build and run JAR
mvn clean package
java -jar target/appliance-monitor-1.0.0.jar

# Option 2: Run with Maven
mvn spring-boot:run
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

Important: Operation data is stored in-memory only and is lost when the application restarts. This is appropriate for the assignment scope but would need database persistence for production use.

## Business Logic

### Stale Appliance Detection
An appliance needs remediation if:
1. `opStatus == "LIVE"`
2. `lastHeardFromOn` is null OR timestamp is older than 10 minutes

### Processing Flow
1. Monitor service fetches all appliances from external API
2. Filters for stale appliances meeting remediation criteria
3. Adds stale appliances to processing queue with duplicate prevention
4. Processing service polls queue and handles appliances concurrently
5. For each appliance: calls drain API → saves drain operation → calls remediate API → saves remediate operation
6. Failed operations are retried automatically
7. If operations fail all retries they will be removed from the working set and tried again during next fetch of stale appliances

## Error Handling

- API failures: Automatic retry with exponential backoff
- Individual failures: Don't block processing of other appliances  
- Queue overflow: Skips new items, retries in next monitoring cycle
- Network issues: Application continues running and recovers when connectivity returns

## Testing

The application includes comprehensive unit tests covering:
- Business logic and stale detection
- API client behavior and retry logic
- Queue management and concurrency
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
    ├── RemediationProcessor.java         # Processing and remediation
    └── RemediationQueue.java             # Queue management
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
- Better fault isolation - API issues don't delay monitoring cycles
- Components can scale independently based on different needs

### Why In-Memory Storage?
- Assignment requirement: "doesn't need to survive restart"
- Zero setup/configuration required
- Appropriate for prototype/development scope

### Why WebClient Over RestTemplate?
- RestTemplate is deprecated in favor of WebClient
- Better performance with non-blocking I/O
- Built-in retry support integration
- Future-proof choice for Spring ecosystem

### Why One-at-a-Time Processing?
Initially considered batch processing, but one-at-a-time provides several advantages:
- Natural backpressure, system self-regulates under load
- Memory usage is bounded by thread pool + queue size
- Slow APIs don't block entire batches

### Why Bounded ThreadPoolExecutor?
Chose bounded over unbounded to prevent memory issues:
- Rejected tasks return to queue for retry
- Maximum 60 concurrent operations (10 active + 50 queued)
- System slows down rather than crashes under load

## Production Considerations

For production deployment, consider:
- Database persistence (PostgreSQL, MySQL) for operation history and survival of restarts
- Monitoring and alerting for queue depth and failure rates
- Configuration externalization for different environments
- Circuit breaker patterns for external API resilience
- Metrics collection for operational visibility

## Known Limitations

- Operation history lost on application restart (by design for assignment scope)
- Basic error handling without circuit breaker patterns

These limitations are appropriate for the assignment scope and are noted for future enhancement.

## AI Usage

- AI was utilized to help format the README 
- Create initial project structure.
- Debugging
- SpringBoot Configuration
