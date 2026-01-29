# Akto Gateway

A lightweight Java library module providing centralized gateway functionality for the Akto platform.

## Overview

Akto Gateway is a JAR library that can be used by other Akto modules (like database-abstractor) to access gateway features. It provides a central Gateway class with various utility functions for request processing, validation, rate limiting, and activity logging.

## Module Type

- **Packaging:** JAR (Library)
- **Purpose:** Shared library for other services
- **Pattern:** Singleton
- **Usage:** Import as Maven dependency

## Project Structure

```
akto-gateway/
├── pom.xml                              # Maven configuration
├── Readme.md                            # This file
└── src/
    ├── main/
    │   └── java/
    │       └── com/akto/gateway/
    │           └── Gateway.java         # Central Gateway class
    └── test/
        └── java/
            └── com/akto/gateway/
                └── GatewayTest.java     # Unit tests
```

## Building

Build the JAR file using Maven:

```bash
cd /Users/abhijeet/Documents/akto/akto/apps/akto-gateway
mvn clean package
```

This will create `target/akto-gateway.jar` (~4KB)

Run tests:

```bash
mvn test
```

## Using in Other Modules

### Step 1: Add Dependency

In your module's `pom.xml`, add:

```xml
<dependency>
    <groupId>com.akto.apps.gateway</groupId>
    <artifactId>akto-gateway</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 2: Use Gateway in Your Code

```java
import com.akto.gateway.Gateway;
import java.util.HashMap;
import java.util.Map;

public class YourService {
    private Gateway gateway = Gateway.getInstance();

    public void processRequest() {
        // Validate request
        Map<String, Object> request = new HashMap<>();
        request.put("action", "getData");

        if (!gateway.validateRequest(request)) {
            throw new IllegalArgumentException("Invalid request");
        }

        // Process through gateway
        Map<String, Object> response = gateway.processRequest(request);

        // Log activity
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user", "admin");
        gateway.logActivity("Request processed", metadata);

        // Get configuration
        Map<String, Object> config = gateway.getConfiguration();
    }
}
```

## Gateway API Reference

### `getInstance()`
Returns the singleton Gateway instance.

**Usage:**
```java
Gateway gateway = Gateway.getInstance();
```

**Returns:** Gateway instance

---

### `validateRequest(Map<String, Object> request)`
Validates a request based on gateway rules.

**Parameters:**
- `request` - Request data to validate

**Returns:** `boolean` - true if valid, false otherwise

**Usage:**
```java
Map<String, Object> request = new HashMap<>();
request.put("data", "value");
boolean isValid = gateway.validateRequest(request);
```

---

### `processRequest(Map<String, Object> request)`
Processes a request through the gateway.

**Parameters:**
- `request` - Request data to process

**Returns:** `Map<String, Object>` - Processed response

**Usage:**
```java
Map<String, Object> request = new HashMap<>();
request.put("action", "test");
Map<String, Object> response = gateway.processRequest(request);
```

---

### `logActivity(String activity, Map<String, Object> metadata)`
Logs gateway activity with metadata.

**Parameters:**
- `activity` - Activity description
- `metadata` - Additional context/metadata

**Usage:**
```java
Map<String, Object> metadata = new HashMap<>();
metadata.put("user", "admin");
metadata.put("ip", "192.168.1.1");
gateway.logActivity("API called", metadata);
```

---

### `getConfiguration()`
Returns gateway configuration settings.

**Returns:** `Map<String, Object>` - Configuration map

**Usage:**
```java
Map<String, Object> config = gateway.getConfiguration();
// Access config values
```

## Dependencies

- **Log4j2** (2.24.2) - Logging framework
- **JUnit** (4.13.2) - Testing framework (test scope)

**Note:** This module uses minimal dependencies to remain lightweight. The parent application controls logging configuration.

## Example: Integration with database-abstractor

### 1. Add dependency to database-abstractor's pom.xml

```xml
<dependency>
    <groupId>com.akto.apps.gateway</groupId>
    <artifactId>akto-gateway</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 2. Use in your service class

```java
package com.akto.database.abstractor;

import com.akto.gateway.Gateway;
import java.util.Map;
import java.util.HashMap;

public class DatabaseAbstractorService {
    private final Gateway gateway = Gateway.getInstance();

    public Map<String, Object> handleDatabaseQuery(Map<String, Object> query) {
        // Validate the query
        if (!gateway.validateRequest(query)) {
            throw new IllegalArgumentException("Invalid database query");
        }

        // Log the activity
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("queryType", query.get("type"));
        metadata.put("table", query.get("table"));
        gateway.logActivity("Database query executed", metadata);

        // Process the query through gateway
        Map<String, Object> result = gateway.processRequest(query);

        // Execute actual database logic here
        // ...

        return result;
    }

    public void initialize() {
        Map<String, Object> config = gateway.getConfiguration();
        System.out.println("Gateway initialized with config: " + config);
    }
}
```

## Design Patterns

### Singleton Pattern
The Gateway class implements the Singleton pattern to ensure only one instance exists across the application:

```java
private static Gateway instance;

public static synchronized Gateway getInstance() {
    if (instance == null) {
        instance = new Gateway();
    }
    return instance;
}
```

**Benefits:**
- Single point of access
- Shared state across application
- Thread-safe initialization

## Development

### Adding New Functions

1. Add method to `Gateway.java`:
```java
public void myNewFunction() {
    logger.info("New function called");
    // Implementation
}
```

2. Add test to `GatewayTest.java`:
```java
@Test
public void testMyNewFunction() {
    Gateway gateway = Gateway.getInstance();
    gateway.myNewFunction();
    // Assertions
}
```

3. Update this README with usage example

### Testing

Run all tests:
```bash
mvn test
```

Run specific test:
```bash
mvn test -Dtest=GatewayTest#testGetInstance
```

## Build Information

- **Java Version:** 8
- **Maven Version:** 3.x
- **Package Size:** ~4KB (extremely lightweight)
- **Test Coverage:** 6 unit tests

## Notes

- This is a **library module**, not a standalone application
- No Main class (not executable)
- Logging configuration is controlled by the parent application
- Thread-safe singleton implementation
- Minimal dependencies for maximum portability
