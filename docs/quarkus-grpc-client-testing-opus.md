# Testing Quarkus gRPC Client Implementations

## A Comprehensive Step-by-Step Guide for Beginners

---

## Table of Contents

1. [Introduction](#introduction)
2. [Prerequisites](#prerequisites)
3. [Project Setup](#project-setup)
4. [Understanding gRPC Client Types in Quarkus](#understanding-grpc-client-types-in-quarkus)
5. [Creating a Sample gRPC Service and Client](#creating-a-sample-grpc-service-and-client)
6. [Testing Strategies Overview](#testing-strategies-overview)
7. [Unit Testing with Mocks](#unit-testing-with-mocks)
8. [Integration Testing with Real gRPC Server](#integration-testing-with-real-grpc-server)
9. [Testing Streaming gRPC Clients](#testing-streaming-grpc-clients)
10. [Testing Blocking Stubs Clients](#testing-blocking-stubs-clients)
11. [Testing Error Handling](#testing-error-handling)
12. [Testing with Testcontainers](#testing-with-testcontainers)
13. [Best Practices](#best-practices)
14. [Troubleshooting Common Issues](#troubleshooting-common-issues)
15. [Useful Links](#useful-links)

---

## Introduction

Testing gRPC client implementations in Quarkus is crucial for ensuring your microservices communicate correctly with external gRPC services. This guide covers various testing strategies, from unit testing with mocks to full integration testing with real gRPC servers.

**What you will learn:**
- How to mock gRPC clients using `@InjectMock` and Mockito
- How to set up integration tests with real gRPC servers
- How to test different gRPC communication patterns (Unary, Streaming)
- Best practices for testing gRPC clients in Quarkus

---

## Prerequisites

Before starting, ensure you have:

- **Java 17+** installed
- **Maven 3.9+** or **Gradle 8+**
- Basic understanding of:
  - Quarkus framework
  - gRPC concepts (Protocol Buffers, stubs, services)
  - JUnit 5 testing
  - Mockito mocking framework

---

## Project Setup

### Step 1: Create a New Quarkus Project

Create a new Quarkus project with gRPC dependencies:

```bash
mvn io.quarkus.platform:quarkus-maven-plugin:3.17.0:create \
    -DprojectGroupId=com.example \
    -DprojectArtifactId=grpc-client-testing \
    -Dextensions="grpc,quarkus-junit5-mockito"
```

### Step 2: Add Required Dependencies

Add the following dependencies to your `pom.xml`:

```xml
<dependencies>
    <!-- Quarkus gRPC Extension -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-grpc</artifactId>
    </dependency>

    <!-- Testing Dependencies -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5</artifactId>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5-mockito</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- AssertJ for fluent assertions -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>3.26.3</version>
        <scope>test</scope>
    </dependency>
    
    <!-- Awaitility for async testing -->
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

For **Gradle** users, add to `build.gradle`:

```groovy
dependencies {
    implementation 'io.quarkus:quarkus-grpc'
    
    testImplementation 'io.quarkus:quarkus-junit5'
    testImplementation 'io.quarkus:quarkus-junit5-mockito'
    testImplementation 'org.assertj:assertj-core:3.26.3'
    testImplementation 'org.awaitility:awaitility'
}
```

---

## Understanding gRPC Client Types in Quarkus

Quarkus provides several ways to inject gRPC clients:

### 1. Mutiny Service Interface (Recommended for Testing)

```java
import io.quarkus.grpc.GrpcClient;

class MyService {
    @GrpcClient("hello")
    Greeter greeter;  // Mutiny-based reactive interface
}
```

### 2. Blocking Stub

```java
import io.quarkus.grpc.GrpcClient;
import hello.GreeterGrpc.GreeterBlockingStub;

class MyService {
    @GrpcClient("hello")
    GreeterBlockingStub blockingStub;
}
```

### 3. Mutiny Stub

```java
import io.quarkus.grpc.GrpcClient;
import hello.MutinyGreeterGrpc.MutinyGreeterStub;

class MyService {
    @GrpcClient("hello")
    MutinyGreeterStub mutinyStub;
}
```

### 4. gRPC Channel

```java
import io.quarkus.grpc.GrpcClient;
import io.grpc.Channel;

class MyService {
    @GrpcClient("hello")
    Channel channel;
}
```

> **Important:** Only the **Mutiny client interface** can be mocked using `@InjectMock`. Other stubs and channels do not support mocking in Quarkus tests.

---

## Creating a Sample gRPC Service and Client

### Step 1: Define the Protocol Buffer

Create a file at `src/main/proto/greeter.proto`:

```protobuf
syntax = "proto3";

option java_multiple_files = true;
option java_package = "com.example.grpc";
option java_outer_classname = "GreeterProto";

package greeter;

// The greeting service definition
service Greeter {
    // Unary RPC
    rpc SayHello (HelloRequest) returns (HelloReply) {}
    
    // Server streaming RPC
    rpc SayHelloStream (HelloRequest) returns (stream HelloReply) {}
    
    // Client streaming RPC
    rpc SayHelloClientStream (stream HelloRequest) returns (HelloReply) {}
    
    // Bidirectional streaming RPC
    rpc SayHelloBidirectional (stream HelloRequest) returns (stream HelloReply) {}
}

message HelloRequest {
    string name = 1;
    string language = 2;
}

message HelloReply {
    string message = 1;
    int64 timestamp = 2;
}
```

### Step 2: Create a Service That Consumes gRPC Client

Create `src/main/java/com/example/service/GreetingService.java`:

```java
package com.example.service;

import com.example.grpc.Greeter;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class GreetingService {

    @GrpcClient("greeter")
    Greeter greeterClient;

    /**
     * Sends a greeting request using unary RPC.
     */
    public Uni<String> greet(String name) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .setLanguage("en")
                .build();
        
        return greeterClient.sayHello(request)
                .map(HelloReply::getMessage);
    }

    /**
     * Sends a greeting request with timeout handling.
     */
    public Uni<String> greetWithTimeout(String name, Duration timeout) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .setLanguage("en")
                .build();
        
        return greeterClient.sayHello(request)
                .ifNoItem().after(timeout).fail()
                .map(HelloReply::getMessage)
                .onFailure().recoverWithItem("Greeting service unavailable");
    }

    /**
     * Receives stream of greetings using server streaming RPC.
     */
    public Multi<String> greetStream(String name) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .setLanguage("en")
                .build();
        
        return greeterClient.sayHelloStream(request)
                .map(HelloReply::getMessage);
    }

    /**
     * Sends multiple names and receives a combined greeting.
     */
    public Uni<String> greetMultiple(List<String> names) {
        Multi<HelloRequest> requests = Multi.createFrom().iterable(names)
                .map(name -> HelloRequest.newBuilder()
                        .setName(name)
                        .setLanguage("en")
                        .build());
        
        return greeterClient.sayHelloClientStream(requests)
                .map(HelloReply::getMessage);
    }
}
```

### Step 3: Configure the gRPC Client

Add to `src/main/resources/application.properties`:

```properties
# gRPC Client Configuration
quarkus.grpc.clients.greeter.host=localhost
quarkus.grpc.clients.greeter.port=9000

# Optional: Enable plaintext (no TLS) for development
quarkus.grpc.clients.greeter.plain-text=true

# Optional: Set deadline for requests
quarkus.grpc.clients.greeter.deadline=5s
```

---

## Testing Strategies Overview

There are three main strategies for testing gRPC clients in Quarkus:

| Strategy | Use Case | Complexity | Speed |
|----------|----------|------------|-------|
| **Unit Testing with Mocks** | Test business logic in isolation | Low | Fast |
| **Integration Testing** | Test with in-process gRPC server | Medium | Medium |
| **Container Testing** | Test with external gRPC service | High | Slow |

---

## Unit Testing with Mocks

The simplest and fastest approach is to mock the gRPC client using `@InjectMock` and Mockito.

### Step 1: Create a Basic Mock Test

Create `src/test/java/com/example/service/GreetingServiceMockTest.java`:

```java
package com.example.service;

import com.example.grpc.Greeter;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class GreetingServiceMockTest {

    @InjectMock
    @GrpcClient("greeter")
    Greeter greeterClient;

    @Inject
    GreetingService greetingService;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        Mockito.reset(greeterClient);
    }

    @Test
    @DisplayName("Should return greeting message for valid name")
    void testGreet_Success() {
        // Arrange
        String expectedMessage = "Hello, John!";
        HelloReply reply = HelloReply.newBuilder()
                .setMessage(expectedMessage)
                .setTimestamp(System.currentTimeMillis())
                .build();
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().item(reply));

        // Act
        String result = greetingService.greet("John")
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(result).isEqualTo(expectedMessage);
    }

    @Test
    @DisplayName("Should capture and verify request parameters")
    void testGreet_VerifyRequestParameters() {
        // Arrange
        HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hello!")
                .build();
        
        ArgumentCaptor<HelloRequest> requestCaptor = 
                ArgumentCaptor.forClass(HelloRequest.class);
        
        when(greeterClient.sayHello(requestCaptor.capture()))
                .thenReturn(Uni.createFrom().item(reply));

        // Act
        greetingService.greet("Alice").await().atMost(Duration.ofSeconds(5));

        // Assert
        HelloRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getName()).isEqualTo("Alice");
        assertThat(capturedRequest.getLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("Should verify gRPC client was called exactly once")
    void testGreet_VerifyInvocationCount() {
        // Arrange
        HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hello!")
                .build();
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().item(reply));

        // Act
        greetingService.greet("Bob").await().atMost(Duration.ofSeconds(5));

        // Assert
        verify(greeterClient, times(1)).sayHello(any(HelloRequest.class));
        verifyNoMoreInteractions(greeterClient);
    }

    @Test
    @DisplayName("Should handle timeout and return fallback message")
    void testGreetWithTimeout_Fallback() {
        // Arrange - simulate a slow response
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().item(HelloReply.newBuilder()
                        .setMessage("Hello!")
                        .build())
                        .onItem().delayIt().by(Duration.ofSeconds(10)));

        // Act - with a short timeout
        String result = greetingService.greetWithTimeout("Charlie", Duration.ofMillis(100))
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(result).isEqualTo("Greeting service unavailable");
    }
}
```

### Step 2: Testing Error Scenarios

Create `src/test/java/com/example/service/GreetingServiceErrorTest.java`:

```java
package com.example.service;

import com.example.grpc.Greeter;
import com.example.grpc.HelloRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class GreetingServiceErrorTest {

    @InjectMock
    @GrpcClient("greeter")
    Greeter greeterClient;

    @Inject
    GreetingService greetingService;

    @Test
    @DisplayName("Should propagate NOT_FOUND status exception")
    void testGreet_NotFound() {
        // Arrange
        StatusRuntimeException exception = new StatusRuntimeException(
                Status.NOT_FOUND.withDescription("User not found"));
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().failure(exception));

        // Act & Assert
        assertThatThrownBy(() -> 
                greetingService.greet("Unknown")
                        .await().atMost(Duration.ofSeconds(5)))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("NOT_FOUND");
    }

    @Test
    @DisplayName("Should propagate UNAVAILABLE status exception")
    void testGreet_Unavailable() {
        // Arrange
        StatusRuntimeException exception = new StatusRuntimeException(
                Status.UNAVAILABLE.withDescription("Service temporarily unavailable"));
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().failure(exception));

        // Act & Assert
        assertThatThrownBy(() -> 
                greetingService.greet("John")
                        .await().atMost(Duration.ofSeconds(5)))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("UNAVAILABLE");
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, 
                names = {"INVALID_ARGUMENT", "PERMISSION_DENIED", "INTERNAL"})
    @DisplayName("Should handle various gRPC status codes")
    void testGreet_VariousStatusCodes(Status.Code statusCode) {
        // Arrange
        StatusRuntimeException exception = new StatusRuntimeException(
                Status.fromCode(statusCode).withDescription("Error: " + statusCode));
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().failure(exception));

        // Act & Assert
        assertThatThrownBy(() -> 
                greetingService.greet("Test")
                        .await().atMost(Duration.ofSeconds(5)))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining(statusCode.name());
    }

    @Test
    @DisplayName("Should handle runtime exceptions gracefully")
    void testGreet_RuntimeException() {
        // Arrange
        RuntimeException exception = new RuntimeException("Unexpected error");
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().failure(exception));

        // Act & Assert
        assertThatThrownBy(() -> 
                greetingService.greet("Test")
                        .await().atMost(Duration.ofSeconds(5)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected error");
    }
}
```

---

## Integration Testing with Real gRPC Server

For integration testing, you can use an in-process gRPC server.

### Step 1: Create a Test gRPC Service Implementation

Create `src/test/java/com/example/grpc/TestGreeterService.java`:

```java
package com.example.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@GrpcService
public class TestGreeterService implements Greeter {

    @Override
    public Uni<HelloReply> sayHello(HelloRequest request) {
        String message = String.format("Hello, %s!", request.getName());
        HelloReply reply = HelloReply.newBuilder()
                .setMessage(message)
                .setTimestamp(System.currentTimeMillis())
                .build();
        return Uni.createFrom().item(reply);
    }

    @Override
    public Multi<HelloReply> sayHelloStream(HelloRequest request) {
        return Multi.createFrom().range(1, 4)
                .onItem().transformToUniAndMerge(i -> 
                    Uni.createFrom().item(HelloReply.newBuilder()
                            .setMessage(String.format("Hello #%d, %s!", i, request.getName()))
                            .setTimestamp(System.currentTimeMillis())
                            .build())
                        .onItem().delayIt().by(Duration.ofMillis(100))
                );
    }

    @Override
    public Uni<HelloReply> sayHelloClientStream(Multi<HelloRequest> requests) {
        return requests
                .map(HelloRequest::getName)
                .collect().asList()
                .map(names -> {
                    String combined = String.join(", ", names);
                    return HelloReply.newBuilder()
                            .setMessage("Hello to all: " + combined + "!")
                            .setTimestamp(System.currentTimeMillis())
                            .build();
                });
    }

    @Override
    public Multi<HelloReply> sayHelloBidirectional(Multi<HelloRequest> requests) {
        AtomicInteger counter = new AtomicInteger(0);
        return requests.map(request -> 
                HelloReply.newBuilder()
                        .setMessage(String.format("Response #%d: Hello, %s!", 
                                counter.incrementAndGet(), request.getName()))
                        .setTimestamp(System.currentTimeMillis())
                        .build());
    }
}
```

### Step 2: Create Integration Test

Create `src/test/java/com/example/service/GreetingServiceIntegrationTest.java`:

```java
package com.example.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class GreetingServiceIntegrationTest {

    @Inject
    GreetingService greetingService;

    @Test
    @DisplayName("Should call real gRPC service and receive greeting")
    void testGreet_Integration() {
        // Act
        String result = greetingService.greet("Integration Test")
                .await().atMost(Duration.ofSeconds(10));

        // Assert
        assertThat(result).isEqualTo("Hello, Integration Test!");
    }

    @Test
    @DisplayName("Should receive stream of greetings")
    void testGreetStream_Integration() {
        // Act
        List<String> results = greetingService.greetStream("StreamTest")
                .collect().asList()
                .await().atMost(Duration.ofSeconds(10));

        // Assert
        assertThat(results)
                .hasSize(3)
                .allMatch(msg -> msg.contains("StreamTest"));
    }

    @Test
    @DisplayName("Should send multiple names and receive combined greeting")
    void testGreetMultiple_Integration() {
        // Act
        String result = greetingService.greetMultiple(List.of("Alice", "Bob", "Charlie"))
                .await().atMost(Duration.ofSeconds(10));

        // Assert
        assertThat(result)
                .contains("Alice")
                .contains("Bob")
                .contains("Charlie");
    }
}
```

### Step 3: Configure Test Properties

Create `src/test/resources/application.properties`:

```properties
# Configure gRPC client to connect to the in-process test server
quarkus.grpc.clients.greeter.host=localhost
quarkus.grpc.clients.greeter.port=9000

# Use the Quarkus gRPC server port for testing
quarkus.grpc.server.port=9000
quarkus.grpc.server.test-port=9000

# Enable plaintext for testing
quarkus.grpc.clients.greeter.plain-text=true
```

---

## Testing Streaming gRPC Clients

### Testing Server Streaming

```java
package com.example.service;

import com.example.grpc.Greeter;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class StreamingGreetingServiceTest {

    @InjectMock
    @GrpcClient("greeter")
    Greeter greeterClient;

    @Inject
    GreetingService greetingService;

    @Test
    @DisplayName("Should handle server streaming responses")
    void testGreetStream_MockedResponses() {
        // Arrange - Create a stream of 5 mock replies
        Multi<HelloReply> mockStream = Multi.createFrom().range(1, 6)
                .map(i -> HelloReply.newBuilder()
                        .setMessage("Greeting #" + i)
                        .setTimestamp(System.currentTimeMillis())
                        .build());

        when(greeterClient.sayHelloStream(any(HelloRequest.class)))
                .thenReturn(mockStream);

        // Act
        List<String> results = greetingService.greetStream("Test")
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(results)
                .hasSize(5)
                .containsExactly(
                        "Greeting #1",
                        "Greeting #2",
                        "Greeting #3",
                        "Greeting #4",
                        "Greeting #5"
                );
    }

    @Test
    @DisplayName("Should handle empty stream gracefully")
    void testGreetStream_EmptyStream() {
        // Arrange
        when(greeterClient.sayHelloStream(any(HelloRequest.class)))
                .thenReturn(Multi.createFrom().empty());

        // Act
        List<String> results = greetingService.greetStream("Test")
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should handle stream errors")
    void testGreetStream_Error() {
        // Arrange
        RuntimeException streamError = new RuntimeException("Stream interrupted");
        when(greeterClient.sayHelloStream(any(HelloRequest.class)))
                .thenReturn(Multi.createFrom().<HelloReply>failure(streamError));

        // Act & Assert
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                greetingService.greetStream("Test")
                        .collect().asList()
                        .await().atMost(Duration.ofSeconds(5)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Stream interrupted");
    }
}
```

### Testing Client Streaming

```java
package com.example.service;

import com.example.grpc.Greeter;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class ClientStreamingTest {

    @InjectMock
    @GrpcClient("greeter")
    Greeter greeterClient;

    @Inject
    GreetingService greetingService;

    @Test
    @DisplayName("Should send client stream and receive single response")
    void testGreetMultiple_MockedResponse() {
        // Arrange
        HelloReply mockReply = HelloReply.newBuilder()
                .setMessage("Hello to all: Alice, Bob, Charlie!")
                .setTimestamp(System.currentTimeMillis())
                .build();

        // Capture the Multi argument to verify what was sent
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Multi<HelloRequest>> requestCaptor = 
                ArgumentCaptor.forClass(Multi.class);

        when(greeterClient.sayHelloClientStream(any()))
                .thenAnswer(invocation -> {
                    // Consume the stream to simulate processing
                    Multi<HelloRequest> requests = invocation.getArgument(0);
                    return requests.collect().asList()
                            .replaceWith(mockReply);
                });

        // Act
        String result = greetingService.greetMultiple(List.of("Alice", "Bob", "Charlie"))
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(result).isEqualTo("Hello to all: Alice, Bob, Charlie!");
    }

    @Test
    @DisplayName("Should handle empty client stream")
    void testGreetMultiple_EmptyList() {
        // Arrange
        HelloReply mockReply = HelloReply.newBuilder()
                .setMessage("Hello to all: !")
                .setTimestamp(System.currentTimeMillis())
                .build();

        when(greeterClient.sayHelloClientStream(any()))
                .thenReturn(Uni.createFrom().item(mockReply));

        // Act
        String result = greetingService.greetMultiple(List.of())
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(result).isEqualTo("Hello to all: !");
    }
}
```

---

## Testing Blocking Stubs Clients

Blocking stubs (`GreeterBlockingStub`) provide a synchronous API for gRPC calls. Unlike Mutiny clients, **blocking stubs cannot be directly mocked** using `@InjectMock` in Quarkus. This section covers alternative testing strategies for services that use blocking stubs.

### Understanding the Limitation

> **Important:** Quarkus's `@InjectMock` annotation only works with Mutiny service interfaces. Blocking stubs, async stubs, and gRPC channels cannot be mocked directly.

### Strategy 1: Wrapper Pattern (Recommended for Unit Testing)

The best approach for testing blocking stubs is to wrap them in a service layer that can be mocked.

#### Step 1: Create a Blocking Stub Wrapper Service

Create `src/main/java/com/example/service/BlockingGreeterClient.java`:

```java
package com.example.service;

import com.example.grpc.GreeterGrpc.GreeterBlockingStub;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper service for the blocking gRPC stub.
 * This wrapper can be mocked in tests.
 */
@ApplicationScoped
public class BlockingGreeterClient {

    @GrpcClient("greeter")
    GreeterBlockingStub blockingStub;

    /**
     * Sends a simple greeting request using blocking API.
     */
    public String sayHello(String name) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .setLanguage("en")
                .build();
        
        HelloReply reply = blockingStub.sayHello(request);
        return reply.getMessage();
    }

    /**
     * Sends a greeting request with a custom deadline.
     */
    public String sayHelloWithDeadline(String name, long timeoutMs) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .setLanguage("en")
                .build();
        
        HelloReply reply = blockingStub
                .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                .sayHello(request);
        
        return reply.getMessage();
    }

    /**
     * Receives a stream of greetings (server streaming) using blocking iterator.
     */
    public Iterator<HelloReply> sayHelloStream(String name) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .setLanguage("en")
                .build();
        
        return blockingStub.sayHelloStream(request);
    }

    /**
     * Checks if the gRPC service is available.
     */
    public boolean isServiceAvailable() {
        try {
            HelloRequest request = HelloRequest.newBuilder()
                    .setName("health-check")
                    .build();
            blockingStub
                    .withDeadlineAfter(1000, TimeUnit.MILLISECONDS)
                    .sayHello(request);
            return true;
        } catch (StatusRuntimeException e) {
            return false;
        }
    }
}
```

#### Step 2: Create a Service That Uses the Wrapper

Create `src/main/java/com/example/service/BlockingGreetingService.java`:

```java
package com.example.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Business service that uses the blocking gRPC client wrapper.
 */
@ApplicationScoped
public class BlockingGreetingService {

    @Inject
    BlockingGreeterClient greeterClient;

    /**
     * Gets a greeting with error handling.
     */
    public String getGreeting(String name) {
        try {
            return greeterClient.sayHello(name);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                return "Service is temporarily unavailable";
            }
            throw e;
        }
    }

    /**
     * Gets a greeting with a timeout.
     */
    public String getGreetingWithTimeout(String name, long timeoutMs) {
        try {
            return greeterClient.sayHelloWithDeadline(name, timeoutMs);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                return "Request timed out, please try again";
            }
            throw e;
        }
    }

    /**
     * Collects all greetings from a stream.
     */
    public List<String> getAllGreetings(String name) {
        List<String> greetings = new ArrayList<>();
        var iterator = greeterClient.sayHelloStream(name);
        while (iterator.hasNext()) {
            greetings.add(iterator.next().getMessage());
        }
        return greetings;
    }

    /**
     * Checks service health.
     */
    public boolean isHealthy() {
        return greeterClient.isServiceAvailable();
    }
}
```

#### Step 3: Unit Test with Mocked Wrapper

Create `src/test/java/com/example/service/BlockingGreetingServiceTest.java`:

```java
package com.example.service;

import com.example.grpc.HelloReply;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
class BlockingGreetingServiceTest {

    @InjectMock
    BlockingGreeterClient greeterClient;  // Mock the wrapper, not the stub!

    @Inject
    BlockingGreetingService greetingService;

    @BeforeEach
    void setUp() {
        Mockito.reset(greeterClient);
    }

    @Test
    @DisplayName("Should return greeting from blocking client")
    void testGetGreeting_Success() {
        // Arrange
        when(greeterClient.sayHello("John"))
                .thenReturn("Hello, John!");

        // Act
        String result = greetingService.getGreeting("John");

        // Assert
        assertThat(result).isEqualTo("Hello, John!");
        verify(greeterClient, times(1)).sayHello("John");
    }

    @Test
    @DisplayName("Should handle UNAVAILABLE status gracefully")
    void testGetGreeting_Unavailable() {
        // Arrange
        when(greeterClient.sayHello(anyString()))
                .thenThrow(new StatusRuntimeException(
                        Status.UNAVAILABLE.withDescription("Server down")));

        // Act
        String result = greetingService.getGreeting("John");

        // Assert
        assertThat(result).isEqualTo("Service is temporarily unavailable");
    }

    @Test
    @DisplayName("Should propagate other exceptions")
    void testGetGreeting_OtherErrors() {
        // Arrange
        when(greeterClient.sayHello(anyString()))
                .thenThrow(new StatusRuntimeException(
                        Status.INTERNAL.withDescription("Internal error")));

        // Act & Assert
        assertThatThrownBy(() -> greetingService.getGreeting("John"))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("INTERNAL");
    }

    @Test
    @DisplayName("Should handle timeout with fallback message")
    void testGetGreetingWithTimeout_DeadlineExceeded() {
        // Arrange
        when(greeterClient.sayHelloWithDeadline(anyString(), anyLong()))
                .thenThrow(new StatusRuntimeException(
                        Status.DEADLINE_EXCEEDED.withDescription("Timeout")));

        // Act
        String result = greetingService.getGreetingWithTimeout("John", 1000);

        // Assert
        assertThat(result).isEqualTo("Request timed out, please try again");
    }

    @Test
    @DisplayName("Should collect all greetings from stream")
    void testGetAllGreetings_Success() {
        // Arrange
        List<HelloReply> replies = Arrays.asList(
                HelloReply.newBuilder().setMessage("Hello #1").build(),
                HelloReply.newBuilder().setMessage("Hello #2").build(),
                HelloReply.newBuilder().setMessage("Hello #3").build()
        );
        when(greeterClient.sayHelloStream("Test"))
                .thenReturn(replies.iterator());

        // Act
        List<String> results = greetingService.getAllGreetings("Test");

        // Assert
        assertThat(results)
                .hasSize(3)
                .containsExactly("Hello #1", "Hello #2", "Hello #3");
    }

    @Test
    @DisplayName("Should return true when service is available")
    void testIsHealthy_Available() {
        // Arrange
        when(greeterClient.isServiceAvailable()).thenReturn(true);

        // Act
        boolean result = greetingService.isHealthy();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when service is unavailable")
    void testIsHealthy_Unavailable() {
        // Arrange
        when(greeterClient.isServiceAvailable()).thenReturn(false);

        // Act
        boolean result = greetingService.isHealthy();

        // Assert
        assertThat(result).isFalse();
    }
}
```

### Strategy 2: Integration Testing with Real gRPC Server

For integration tests, the blocking stub works directly with an in-process gRPC server.

Create `src/test/java/com/example/service/BlockingGreeterClientIntegrationTest.java`:

```java
package com.example.service;

import com.example.grpc.HelloReply;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class BlockingGreeterClientIntegrationTest {

    @Inject
    BlockingGreeterClient greeterClient;

    @Test
    @DisplayName("Should call blocking stub and receive response")
    void testSayHello_Integration() {
        // Act
        String result = greeterClient.sayHello("Integration");

        // Assert
        assertThat(result).isEqualTo("Hello, Integration!");
    }

    @Test
    @DisplayName("Should call blocking stub with deadline")
    void testSayHelloWithDeadline_Integration() {
        // Act
        String result = greeterClient.sayHelloWithDeadline("Deadline Test", 5000);

        // Assert
        assertThat(result).isEqualTo("Hello, Deadline Test!");
    }

    @Test
    @DisplayName("Should iterate over blocking stream")
    void testSayHelloStream_Integration() {
        // Act
        Iterator<HelloReply> iterator = greeterClient.sayHelloStream("StreamTest");
        List<String> messages = new ArrayList<>();
        while (iterator.hasNext()) {
            messages.add(iterator.next().getMessage());
        }

        // Assert
        assertThat(messages)
                .isNotEmpty()
                .allMatch(msg -> msg.contains("StreamTest"));
    }

    @Test
    @DisplayName("Should check service availability")
    void testIsServiceAvailable_Integration() {
        // Act
        boolean available = greeterClient.isServiceAvailable();

        // Assert
        assertThat(available).isTrue();
    }
}
```

### Strategy 3: Direct Stub Testing with @GrpcClient

You can also inject and test blocking stubs directly in integration tests.

Create `src/test/java/com/example/service/DirectBlockingStubTest.java`:

```java
package com.example.service;

import com.example.grpc.GreeterGrpc.GreeterBlockingStub;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class DirectBlockingStubTest {

    @GrpcClient("greeter")
    GreeterBlockingStub blockingStub;

    @Test
    @DisplayName("Should make unary call with blocking stub")
    void testUnaryCall() {
        // Arrange
        HelloRequest request = HelloRequest.newBuilder()
                .setName("Direct Test")
                .setLanguage("en")
                .build();

        // Act
        HelloReply reply = blockingStub.sayHello(request);

        // Assert
        assertThat(reply.getMessage()).isEqualTo("Hello, Direct Test!");
    }

    @Test
    @DisplayName("Should make call with deadline")
    void testCallWithDeadline() {
        // Arrange
        HelloRequest request = HelloRequest.newBuilder()
                .setName("Deadline Test")
                .setLanguage("en")
                .build();

        // Act
        HelloReply reply = blockingStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .sayHello(request);

        // Assert
        assertThat(reply.getMessage()).isEqualTo("Hello, Deadline Test!");
    }

    @Test
    @DisplayName("Should iterate server streaming response")
    void testServerStreaming() {
        // Arrange
        HelloRequest request = HelloRequest.newBuilder()
                .setName("Stream Test")
                .setLanguage("en")
                .build();

        // Act
        Iterator<HelloReply> responses = blockingStub.sayHelloStream(request);
        List<String> messages = new ArrayList<>();
        responses.forEachRemaining(reply -> messages.add(reply.getMessage()));

        // Assert
        assertThat(messages)
                .isNotEmpty()
                .allMatch(msg -> msg.contains("Stream Test"));
    }

    @Test
    @DisplayName("Should handle very short deadline with DEADLINE_EXCEEDED")
    void testDeadlineExceeded() {
        // Arrange
        HelloRequest request = HelloRequest.newBuilder()
                .setName("Slow Request")
                .setLanguage("en")
                .build();

        // Act & Assert - using an extremely short deadline
        // Note: This may or may not throw depending on server speed
        // This is an example of testing deadline behavior
        try {
            HelloReply reply = blockingStub
                    .withDeadlineAfter(1, TimeUnit.NANOSECONDS) // Extremely short
                    .sayHello(request);
            // If we get here, the call was faster than expected
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode())
                    .isIn(Status.Code.DEADLINE_EXCEEDED, Status.Code.CANCELLED);
        }
    }
}
```

### Strategy 4: Testing with Mockito Inline (Manual Stub Mocking)

If you must mock the blocking stub directly (not recommended), you can use plain Mockito without `@InjectMock`.

Create `src/test/java/com/example/service/ManualBlockingStubMockTest.java`:

```java
package com.example.service;

import com.example.grpc.GreeterGrpc.GreeterBlockingStub;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit test using plain Mockito (without Quarkus test context).
 * This is useful for testing business logic in complete isolation.
 */
@ExtendWith(MockitoExtension.class)
class ManualBlockingStubMockTest {

    @Mock
    GreeterBlockingStub mockBlockingStub;

    // Manually create the service with the mock
    private BlockingGreeterClient greeterClient;

    @BeforeEach
    void setUp() {
        // Create a test instance with the mock injected
        greeterClient = new BlockingGreeterClientTestable(mockBlockingStub);
    }

    @Test
    @DisplayName("Should call mocked blocking stub")
    void testSayHello_WithMock() {
        // Arrange
        HelloReply mockReply = HelloReply.newBuilder()
                .setMessage("Mocked Hello!")
                .build();
        when(mockBlockingStub.sayHello(any(HelloRequest.class)))
                .thenReturn(mockReply);

        // Act
        String result = greeterClient.sayHello("Test");

        // Assert
        assertThat(result).isEqualTo("Mocked Hello!");
    }

    /**
     * Testable subclass that allows injecting a mock stub.
     */
    static class BlockingGreeterClientTestable extends BlockingGreeterClient {
        private final GreeterBlockingStub stubOverride;

        public BlockingGreeterClientTestable(GreeterBlockingStub stub) {
            this.stubOverride = stub;
        }

        @Override
        public String sayHello(String name) {
            HelloRequest request = HelloRequest.newBuilder()
                    .setName(name)
                    .setLanguage("en")
                    .build();
            return stubOverride.sayHello(request).getMessage();
        }
    }
}
```

### Comparison: Mutiny vs Blocking Stubs Testing

| Aspect | Mutiny Client | Blocking Stub |
|--------|--------------|---------------|
| **`@InjectMock` Support** | ✅ Yes | ❌ No |
| **Unit Testing** | Direct mocking | Wrapper pattern |
| **Integration Testing** | Works directly | Works directly |
| **Async Support** | Native (Uni/Multi) | None (use Executor) |
| **Streaming** | Reactive streams | Iterator-based |
| **Recommended For** | Most use cases | Legacy/simple sync code |

### Best Practices for Blocking Stubs Testing

1. **Prefer Wrapper Pattern**: Create an `@ApplicationScoped` wrapper around blocking stubs for testability.

2. **Use Integration Tests**: For blocking stubs, integration tests with a real gRPC server are often more reliable than mocking.

3. **Set Appropriate Deadlines**: Always configure timeouts for blocking calls to prevent hanging tests.

4. **Handle Exceptions Explicitly**: Blocking stubs throw `StatusRuntimeException` directly, so test exception handling.

5. **Consider Mutiny Migration**: If testing is a major concern, consider migrating to Mutiny clients for better mockability.

```java
// Example: Setting deadline in tests
@Test
void testWithDeadline() {
    HelloReply reply = blockingStub
            .withDeadlineAfter(5, TimeUnit.SECONDS)
            .sayHello(request);
    // ...
}
```

---

## Testing Error Handling

### Creating an Error-Resilient Service

Create `src/main/java/com/example/service/ResilientGreetingService.java`:

```java
package com.example.service;

import com.example.grpc.Greeter;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class ResilientGreetingService {

    @GrpcClient("greeter")
    Greeter greeterClient;

    @Retry(maxRetries = 3, delay = 100, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "fallbackGreet")
    @CircuitBreaker(requestVolumeThreshold = 4, 
                    failureRatio = 0.5, 
                    delay = 10000)
    public Uni<String> greetWithResilience(String name) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .setLanguage("en")
                .build();
        
        return greeterClient.sayHello(request)
                .map(HelloReply::getMessage);
    }

    public Uni<String> fallbackGreet(String name) {
        return Uni.createFrom().item("Hello, " + name + "! (Fallback response)");
    }

    /**
     * Handles specific gRPC status codes with custom error messages.
     */
    public Uni<String> greetWithErrorHandling(String name) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .setLanguage("en")
                .build();
        
        return greeterClient.sayHello(request)
                .map(HelloReply::getMessage)
                .onFailure(StatusRuntimeException.class)
                .recoverWithItem(throwable -> {
                    StatusRuntimeException sre = (StatusRuntimeException) throwable;
                    Status.Code code = sre.getStatus().getCode();
                    
                    return switch (code) {
                        case UNAVAILABLE -> "Service is temporarily unavailable";
                        case DEADLINE_EXCEEDED -> "Request timed out";
                        case INVALID_ARGUMENT -> "Invalid request parameters";
                        case PERMISSION_DENIED -> "Access denied";
                        default -> "An error occurred: " + sre.getMessage();
                    };
                });
    }
}
```

### Testing Resilience Patterns

Create `src/test/java/com/example/service/ResilientGreetingServiceTest.java`:

```java
package com.example.service;

import com.example.grpc.Greeter;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class ResilientGreetingServiceTest {

    @InjectMock
    @GrpcClient("greeter")
    Greeter greeterClient;

    @Inject
    ResilientGreetingService resilientService;

    @BeforeEach
    void setUp() {
        Mockito.reset(greeterClient);
    }

    @Test
    @DisplayName("Should return fallback response when service is unavailable")
    void testFallback_WhenUnavailable() {
        // Arrange
        StatusRuntimeException exception = new StatusRuntimeException(
                Status.UNAVAILABLE.withDescription("Service down"));
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().failure(exception));

        // Act
        String result = resilientService.greetWithResilience("Test")
                .await().atMost(Duration.ofSeconds(30));

        // Assert - should get fallback response after retries
        assertThat(result).isEqualTo("Hello, Test! (Fallback response)");
    }

    @Test
    @DisplayName("Should handle UNAVAILABLE status with custom message")
    void testErrorHandling_Unavailable() {
        // Arrange
        StatusRuntimeException exception = new StatusRuntimeException(
                Status.UNAVAILABLE.withDescription("Service down"));
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().failure(exception));

        // Act
        String result = resilientService.greetWithErrorHandling("Test")
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(result).isEqualTo("Service is temporarily unavailable");
    }

    @Test
    @DisplayName("Should handle DEADLINE_EXCEEDED status with custom message")
    void testErrorHandling_DeadlineExceeded() {
        // Arrange
        StatusRuntimeException exception = new StatusRuntimeException(
                Status.DEADLINE_EXCEEDED.withDescription("Deadline exceeded"));
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().failure(exception));

        // Act
        String result = resilientService.greetWithErrorHandling("Test")
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(result).isEqualTo("Request timed out");
    }

    @Test
    @DisplayName("Should handle INVALID_ARGUMENT status with custom message")
    void testErrorHandling_InvalidArgument() {
        // Arrange
        StatusRuntimeException exception = new StatusRuntimeException(
                Status.INVALID_ARGUMENT.withDescription("Invalid name"));
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().failure(exception));

        // Act
        String result = resilientService.greetWithErrorHandling("Test")
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(result).isEqualTo("Invalid request parameters");
    }

    @Test
    @DisplayName("Should handle PERMISSION_DENIED status with custom message")
    void testErrorHandling_PermissionDenied() {
        // Arrange
        StatusRuntimeException exception = new StatusRuntimeException(
                Status.PERMISSION_DENIED.withDescription("Access forbidden"));
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().failure(exception));

        // Act
        String result = resilientService.greetWithErrorHandling("Test")
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(result).isEqualTo("Access denied");
    }
}
```

---

## Testing with Testcontainers

For testing against a real gRPC service running in a container:

### Step 1: Add Testcontainers Dependencies

Add to `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-test-common</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### Step 2: Create Quarkus Test Resource

Create `src/test/java/com/example/testresource/GrpcServerTestResource.java`:

```java
package com.example.testresource;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.HashMap;
import java.util.Map;

public class GrpcServerTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> grpcContainer;

    @Override
    public Map<String, String> start() {
        // Example: using a pre-built gRPC server image
        grpcContainer = new GenericContainer<>("your-grpc-server-image:latest")
                .withExposedPorts(9000)
                .waitingFor(Wait.forListeningPort());
        
        grpcContainer.start();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.grpc.clients.greeter.host", grpcContainer.getHost());
        config.put("quarkus.grpc.clients.greeter.port", 
                   String.valueOf(grpcContainer.getMappedPort(9000)));
        config.put("quarkus.grpc.clients.greeter.plain-text", "true");
        
        return config;
    }

    @Override
    public void stop() {
        if (grpcContainer != null) {
            grpcContainer.stop();
        }
    }
}
```

### Step 3: Use the Test Resource

```java
package com.example.service;

import com.example.testresource.GrpcServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(GrpcServerTestResource.class)
class GreetingServiceContainerTest {

    @Inject
    GreetingService greetingService;

    @Test
    @DisplayName("Should communicate with containerized gRPC service")
    void testGreet_WithContainer() {
        // Act
        String result = greetingService.greet("Container Test")
                .await().atMost(Duration.ofSeconds(10));

        // Assert
        assertThat(result).contains("Container Test");
    }
}
```

---

## Best Practices

### 1. **Prefer Mutiny Clients for Testing**

Only Mutiny service interfaces support mocking with `@InjectMock`. Always use:

```java
@GrpcClient("service-name")
ServiceInterface client;  // ✅ Can be mocked
```

Instead of:

```java
@GrpcClient("service-name")
GreeterGrpc.GreeterBlockingStub blockingStub;  // ❌ Cannot be mocked
```

### 2. **Reset Mocks Between Tests**

Always reset mocks in `@BeforeEach` to prevent test pollution:

```java
@BeforeEach
void setUp() {
    Mockito.reset(greeterClient);
}
```

### 3. **Use Descriptive Test Names**

Use `@DisplayName` annotations for clear test documentation:

```java
@Test
@DisplayName("Should return greeting message for valid name")
void testGreet_Success() { ... }
```

### 4. **Test Both Success and Failure Scenarios**

Always test error handling alongside happy paths:

```java
// Happy path
@Test
void testGreet_Success() { ... }

// Error scenarios
@Test
void testGreet_NotFound() { ... }

@Test
void testGreet_Unavailable() { ... }

@Test
void testGreet_Timeout() { ... }
```

### 5. **Use Appropriate Timeouts**

Set reasonable timeouts for async operations:

```java
// Good - explicit timeout
result.await().atMost(Duration.ofSeconds(5));

// Avoid - indefinite wait can hang tests
result.await().indefinitely();
```

### 6. **Verify Interactions When Important**

Use Mockito's verification when the call itself is the test target:

```java
verify(greeterClient, times(1)).sayHello(any());
verifyNoMoreInteractions(greeterClient);
```

### 7. **Use ArgumentCaptor for Complex Verification**

Capture arguments to verify request construction:

```java
ArgumentCaptor<HelloRequest> captor = ArgumentCaptor.forClass(HelloRequest.class);
verify(greeterClient).sayHello(captor.capture());
assertThat(captor.getValue().getName()).isEqualTo("ExpectedName");
```

### 8. **Separate Unit and Integration Tests**

Use Maven profiles or naming conventions to separate test types:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>**/*IntegrationTest.java</exclude>
        </excludes>
    </configuration>
</plugin>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*IntegrationTest.java</include>
        </includes>
    </configuration>
</plugin>
```

---

## Troubleshooting Common Issues

### Issue 1: Mock Not Being Injected

**Symptom:** Tests use the real gRPC client instead of the mock.

**Solution:** Ensure you're using the Mutiny service interface, not stubs:

```java
// ✅ Correct - service interface
@InjectMock
@GrpcClient("greeter")
Greeter greeterClient;

// ❌ Wrong - blocking stub cannot be mocked
@InjectMock
@GrpcClient("greeter")
GreeterGrpc.GreeterBlockingStub blockingStub;
```

### Issue 2: Connection Refused in Tests

**Symptom:** `io.grpc.StatusRuntimeException: UNAVAILABLE: io exception`

**Solution:** Ensure test configuration matches server configuration:

```properties
# src/test/resources/application.properties
quarkus.grpc.clients.greeter.host=localhost
quarkus.grpc.clients.greeter.port=9000
quarkus.grpc.server.port=9000
quarkus.grpc.clients.greeter.plain-text=true
```

### Issue 3: Tests Hang Indefinitely

**Symptom:** Tests don't complete and must be killed.

**Solution:** Always use explicit timeouts:

```java
// Instead of
result.await().indefinitely();

// Use
result.await().atMost(Duration.ofSeconds(5));
```

### Issue 4: Protocol Buffer Classes Not Found

**Symptom:** `ClassNotFoundException` for generated protobuf classes.

**Solution:** Ensure protobuf compilation runs before tests:

```bash
mvn compile  # Generate proto classes first
mvn test     # Then run tests
```

### Issue 5: Mock Returning Null

**Symptom:** `NullPointerException` when calling mocked methods.

**Solution:** Ensure mock is properly configured:

```java
@BeforeEach
void setUp() {
    HelloReply reply = HelloReply.newBuilder()
            .setMessage("Hello")
            .build();
    // Configure the mock behavior
    when(greeterClient.sayHello(any()))
            .thenReturn(Uni.createFrom().item(reply));
}
```

### Issue 6: @InjectMock Not Working with @QuarkusIntegrationTest

**Symptom:** Mocks don't work in integration tests.

**Solution:** `@InjectMock` only works with `@QuarkusTest`, not `@QuarkusIntegrationTest`. Use a real gRPC server for integration testing.

---

## Useful Links

### Official Documentation
- [Quarkus gRPC Service Consumption Guide](https://quarkus.io/guides/grpc-service-consumption)
- [Quarkus gRPC Service Implementation Guide](https://quarkus.io/guides/grpc-service-implementation)
- [Quarkus Testing Guide](https://quarkus.io/guides/getting-started-testing)
- [Quarkus Testing with Mockito](https://quarkus.io/guides/getting-started-testing#mock-support)

### Related Technologies
- [SmallRye Mutiny Documentation](https://smallrye.io/smallrye-mutiny/)
- [gRPC Official Documentation](https://grpc.io/docs/)
- [Protocol Buffers Language Guide](https://developers.google.com/protocol-buffers/docs/proto3)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/index.html)

### Additional Resources
- [Quarkus GitHub Repository](https://github.com/quarkusio/quarkus)
- [Quarkus gRPC Quickstart](https://github.com/quarkusio/quarkus-quickstarts/tree/main/grpc-plain-text-quickstart)
- [Testcontainers Documentation](https://www.testcontainers.org/)

### Community
- [Quarkus Stack Overflow](https://stackoverflow.com/questions/tagged/quarkus)
- [Quarkus GitHub Discussions](https://github.com/quarkusio/quarkus/discussions)
- [Quarkus Zulip Chat](https://quarkusio.zulipchat.com/)

---

## Summary

This guide covered the essential strategies for testing Quarkus gRPC client implementations:

1. **Unit Testing with Mocks** - Fast, isolated tests using `@InjectMock` and Mockito
2. **Integration Testing** - Tests against a real in-process gRPC server
3. **Container Testing** - Tests against containerized gRPC services using Testcontainers
4. **Error Handling Testing** - Verifying proper handling of gRPC status codes
5. **Streaming Tests** - Testing server streaming, client streaming, and bidirectional patterns

Remember:
- Only **Mutiny service interfaces** can be mocked
- Always use **explicit timeouts** in async tests
- **Reset mocks** between tests to prevent pollution
- Test both **success and failure** scenarios

Happy testing! 🚀
