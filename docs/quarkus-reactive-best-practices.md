# Quarkus Reactive Best Practices Guide

## Using Mutiny with gRPC Clients

---

## Table of Contents

1. [Mutiny vs Blocking Stubs](#mutiny-vs-blocking-stubs)
2. [Reactive Flow Pattern](#reactive-flow-pattern)
3. [Working with Uni and Multi](#working-with-uni-and-multi)
4. [Consuming Reactive Types in Applications](#consuming-reactive-types-in-applications)
5. [Integration with Telegram Bots](#integration-with-telegram-bots)
6. [Testing Mutiny-based gRPC Clients](#testing-mutiny-based-grpc-clients)
7. [Common Patterns and Operators](#common-patterns-and-operators)
8. [Quick Reference](#quick-reference)

---

## Mutiny vs Blocking Stubs

### Recommendation: Use Mutiny Service Interface 🎯

For most Quarkus applications, **Mutiny service interfaces are the recommended choice**.

### Comparison Table

| Criteria | Mutiny Service Interface | Blocking Stub |
|----------|-------------------------|---------------|
| **Testing (Mocking)** | ✅ Easy - `@InjectMock` works directly | ❌ Difficult - requires wrapper pattern |
| **Thread Usage** | ✅ Non-blocking, efficient | ❌ Blocks thread while waiting |
| **Reactive Streams** | ✅ Native `Uni`/`Multi` support | ❌ Uses iterators |
| **Quarkus Integration** | ✅ First-class citizen | ⚠️ Supported but less idiomatic |
| **Error Handling** | ✅ Declarative with operators | ⚠️ Try-catch blocks |
| **Native Compilation** | ✅ Optimized | ✅ Works |
| **Learning Curve** | ⚠️ Requires reactive knowledge | ✅ Familiar sync patterns |

### When to Use Mutiny ✅

- You want easy testing with `@InjectMock`
- High concurrency applications
- Streaming gRPC calls
- Modern Quarkus applications
- Microservices architecture

### When to Use Blocking Stub ⚠️

- Legacy integration with synchronous libraries
- Simple CLI tools or batch jobs
- Team not ready for reactive programming

---

## Reactive Flow Pattern

### The Golden Rule 🌟

> **Propagate `Uni`/`Multi` up the call stack. Let the framework (Quarkus) handle subscription at the boundary (HTTP, Messaging, etc.).**

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     Request Flow                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  HTTP Request                                                   │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────┐    ┌─────────────────┐    ┌──────────────┐    │
│  │ REST        │───▶│ Service Layer   │───▶│ gRPC Client  │    │
│  │ Endpoint    │    │                 │    │ (Mutiny)     │    │
│  │             │◀───│                 │◀───│              │    │
│  │ Returns Uni │    │ Returns Uni     │    │ Returns Uni  │    │
│  └─────────────┘    └─────────────────┘    └──────────────┘    │
│       │                                                         │
│       ▼                                                         │
│  Quarkus subscribes & sends HTTP Response                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Example: End-to-End Reactive

```java
// Service Layer - Returns Uni
@ApplicationScoped
public class GreetingService {

    @GrpcClient("greeter")
    Greeter greeterClient;

    public Uni<String> getGreeting(String name) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .build();
        
        return greeterClient.sayHello(request)
                .map(HelloReply::getMessage);
    }
}

// REST Endpoint - Also Returns Uni
@Path("/greet")
public class GreetingResource {

    @Inject
    GreetingService greetingService;

    @GET
    @Path("/{name}")
    public Uni<String> greet(@PathParam("name") String name) {
        return greetingService.getGreeting(name);
    }
}
```

**Quarkus automatically subscribes and sends the HTTP response!**

---

## Working with Uni and Multi

### Uni - Single Value

`Uni<T>` represents an asynchronous operation that emits **one item or fails**.

```java
// Create Uni
Uni<String> uni = Uni.createFrom().item("Hello");

// Transform
Uni<Integer> length = uni.map(String::length);

// Error handling
Uni<String> safe = uni.onFailure().recoverWithItem("Fallback");

// Chain operations
Uni<Result> chained = uni.chain(value -> anotherAsyncCall(value));
```

### Multi - Multiple Values (Stream)

`Multi<T>` represents an asynchronous stream that emits **0 to N items**.

```java
// Create Multi
Multi<Integer> multi = Multi.createFrom().range(1, 10);

// Transform each item
Multi<String> strings = multi.map(i -> "Item " + i);

// Filter
Multi<Integer> filtered = multi.filter(i -> i > 5);

// Collect to list
Uni<List<Integer>> list = multi.collect().asList();
```

---

## Consuming Reactive Types in Applications

### In REST Endpoints

```java
@GET
public Uni<String> endpoint() {
    return service.getData();  // Quarkus handles subscription
}
```

### In Tests

```java
@Test
void testMethod() {
    String result = service.getData()
            .await().atMost(Duration.ofSeconds(5));  // Block in tests
    assertThat(result).isEqualTo("expected");
}
```

### In Telegram Bots / Event Handlers

```java
public void handleMessage(Update update) {
    String chatId = update.getMessage().getChatId().toString();
    
    service.getData()
        .subscribe().with(
            data -> sendTelegramMessage(chatId, data),
            error -> sendTelegramMessage(chatId, "Error: " + error.getMessage())
        );
}
```

### In CLI / Batch Jobs

```java
public static void main(String[] args) {
    String result = service.getData()
            .await().indefinitely();  // Blocking OK in CLI
    System.out.println(result);
}
```

---

## Integration with Telegram Bots

### Pattern 1: Simple Response with `.subscribe()`

```java
public void handleCommand(Update update) {
    String chatId = update.getMessage().getChatId().toString();
    String userName = update.getMessage().getFrom().getFirstName();

    greetingService.getGreeting(userName)
        .subscribe().with(
            greeting -> sendTelegramMessage(chatId, greeting),
            error -> sendTelegramMessage(chatId, "Error: " + error.getMessage())
        );
}
```

### Pattern 2: Chained gRPC Calls

```java
public void handleOrderCommand(Update update, String orderId) {
    String chatId = update.getMessage().getChatId().toString();

    orderClient.getOrder(orderId)
        .chain(order -> userClient.getUser(order.getUserId())
            .map(user -> formatOrderMessage(order, user)))
        .subscribe().with(
            message -> sendTelegramMessage(chatId, message),
            error -> sendTelegramMessage(chatId, "Failed: " + error.getMessage())
        );
}
```

### Pattern 3: Streaming Data (Multi)

```java
// Collect all, then send
public void handleListCommand(Update update) {
    String chatId = update.getMessage().getChatId().toString();

    productClient.listProducts()  // Multi<Product>
        .collect().asList()        // Uni<List<Product>>
        .map(this::formatProductList)
        .subscribe().with(
            message -> sendTelegramMessage(chatId, message),
            error -> sendTelegramMessage(chatId, "Failed to load products")
        );
}

// Or send each item separately
public void handleStreamCommand(Update update) {
    String chatId = update.getMessage().getChatId().toString();

    productClient.listProducts()  // Multi<Product>
        .subscribe().with(
            product -> sendTelegramMessage(chatId, formatProduct(product)),
            error -> sendTelegramMessage(chatId, "Error: " + error.getMessage()),
            () -> sendTelegramMessage(chatId, "✅ Done!")
        );
}
```

### Pattern 4: With Timeout and Fallback

```java
public void handleGreetCommand(Update update) {
    String chatId = update.getMessage().getChatId().toString();
    String userName = update.getMessage().getFrom().getFirstName();

    greetingService.getGreeting(userName)
        .ifNoItem().after(Duration.ofSeconds(5)).fail()
        .onFailure().recoverWithItem(error -> {
            log.error("gRPC call failed", error);
            return "Hello, " + userName + "! (fallback)";
        })
        .subscribe().with(
            message -> sendTelegramMessage(chatId, message)
        );
}
```

### Pattern 5: Parallel gRPC Calls for Dashboard

```java
public void handleDashboardCommand(Update update, String userId) {
    String chatId = update.getMessage().getChatId().toString();
    
    sendTelegramMessage(chatId, "🔄 Loading your dashboard...");

    // Execute multiple gRPC calls in parallel
    Uni<User> userUni = userClient.getUser(userId);
    Uni<List<Order>> ordersUni = orderClient.getOrders(userId)
            .collect().asList();
    Uni<AccountBalance> balanceUni = accountClient.getBalance(userId);

    Uni.combine().all()
        .unis(userUni, ordersUni, balanceUni)
        .asTuple()
        .ifNoItem().after(Duration.ofSeconds(10)).fail()
        .map(tuple -> formatDashboard(
                tuple.getItem1(),    // User
                tuple.getItem2(),    // List<Order>
                tuple.getItem3()))   // AccountBalance
        .onFailure().recoverWithItem("❌ Failed to load dashboard. Please try again.")
        .subscribe().with(
            message -> sendTelegramMarkdown(chatId, message)
        );
}

private String formatDashboard(User user, List<Order> orders, AccountBalance balance) {
    return String.format("""
        📊 *Dashboard for %s*
        
        👤 *Profile*
        Name: %s
        Email: %s
        
        💰 *Balance*
        Available: $%.2f
        Pending: $%.2f
        
        📦 *Recent Orders*
        Total orders: %d
        %s
        """,
        user.getName(),
        user.getName(),
        user.getEmail(),
        balance.getAvailable(),
        balance.getPending(),
        orders.size(),
        orders.stream()
            .limit(5)
            .map(o -> String.format("• #%s - $%.2f", o.getId(), o.getTotal()))
            .collect(Collectors.joining("\n"))
    );
}
```

### Flow Diagram: Telegram Bot with gRPC

```
┌──────────────┐     ┌─────────────────┐     ┌──────────────┐     ┌──────────────┐
│  Telegram    │     │  Bot Handler    │     │ gRPC Client  │     │ gRPC Server  │
│  Update      │     │                 │     │ (Mutiny)     │     │              │
└──────┬───────┘     └────────┬────────┘     └──────┬───────┘     └──────┬───────┘
       │                      │                     │                    │
       │  1. Receive Update   │                     │                    │
       │─────────────────────▶│                     │                    │
       │                      │                     │                    │
       │                      │  2. Call gRPC      │                    │
       │                      │    (returns Uni)   │                    │
       │                      │────────────────────▶│                    │
       │                      │                     │                    │
       │                      │                     │  3. gRPC Request  │
       │                      │                     │───────────────────▶│
       │                      │                     │                    │
       │                      │                     │  4. gRPC Response │
       │                      │                     │◀───────────────────│
       │                      │                     │                    │
       │                      │  5. Uni emits      │                    │
       │                      │◀────────────────────│                    │
       │                      │                     │                    │
       │  6. Send Response    │                     │                    │
       │◀─────────────────────│                     │                    │
       │     (in callback)    │                     │                    │
       │                      │                     │                    │
```

### Complete Production-Ready Example

#### 1. TelegramSender Helper

```java
package com.example.bot;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@ApplicationScoped
public class TelegramSender {

    private static final Logger LOG = Logger.getLogger(TelegramSender.class);

    @Inject
    TelegramBot bot;

    /**
     * Send a plain text message to a chat.
     */
    public void send(String chatId, String text) {
        try {
            SendMessage message = new SendMessage(chatId, text);
            bot.execute(message);
        } catch (TelegramApiException e) {
            LOG.error("Failed to send Telegram message", e);
        }
    }

    /**
     * Send a markdown-formatted message to a chat.
     */
    public void sendMarkdown(String chatId, String text) {
        try {
            SendMessage message = new SendMessage(chatId, text);
            message.setParseMode("Markdown");
            bot.execute(message);
        } catch (TelegramApiException e) {
            LOG.error("Failed to send Telegram message", e);
        }
    }

    /**
     * Send an HTML-formatted message to a chat.
     */
    public void sendHtml(String chatId, String text) {
        try {
            SendMessage message = new SendMessage(chatId, text);
            message.setParseMode("HTML");
            bot.execute(message);
        } catch (TelegramApiException e) {
            LOG.error("Failed to send Telegram message", e);
        }
    }
}
```

#### 2. Command Handler with gRPC Integration

```java
package com.example.bot;

import com.example.grpc.Order;
import com.example.grpc.Product;
import com.example.service.OrderService;
import com.example.service.ProductService;
import com.example.service.GreetingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Duration;
import java.util.stream.Collectors;

@ApplicationScoped
public class CommandHandler {

    private static final Logger LOG = Logger.getLogger(CommandHandler.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Inject
    GreetingService greetingService;

    @Inject
    ProductService productService;

    @Inject
    OrderService orderService;

    @Inject
    TelegramSender telegram;

    /**
     * Main entry point for handling Telegram updates.
     */
    public void handleUpdate(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String chatId = update.getMessage().getChatId().toString();
        String text = update.getMessage().getText().trim();
        String userName = update.getMessage().getFrom().getFirstName();

        // Route commands
        if (text.startsWith("/start") || text.startsWith("/hello")) {
            handleGreeting(chatId, userName);
        } else if (text.startsWith("/products")) {
            handleProducts(chatId);
        } else if (text.startsWith("/order ")) {
            String orderId = text.substring(7).trim();
            handleOrder(chatId, orderId);
        } else if (text.startsWith("/help")) {
            handleHelp(chatId);
        } else {
            telegram.send(chatId, "❓ Unknown command. Type /help for available commands.");
        }
    }

    /**
     * Handle /hello command - calls gRPC greeting service.
     */
    private void handleGreeting(String chatId, String userName) {
        greetingService.getGreeting(userName)
            .ifNoItem().after(TIMEOUT).fail()
            .onFailure().recoverWithItem(e -> {
                LOG.error("Greeting service failed", e);
                return "👋 Hello, " + userName + "! (Service temporarily unavailable)";
            })
            .subscribe().with(
                greeting -> telegram.send(chatId, greeting)
            );
    }

    /**
     * Handle /products command - streams products from gRPC service.
     */
    private void handleProducts(String chatId) {
        telegram.send(chatId, "🔍 Loading products...");

        productService.listProducts()  // Multi<Product>
            .ifNoItem().after(TIMEOUT).fail()
            .collect().asList()
            .map(products -> {
                if (products.isEmpty()) {
                    return "📦 No products available at the moment.";
                }
                StringBuilder sb = new StringBuilder("📦 *Available Products:*\n\n");
                for (Product p : products) {
                    sb.append(String.format("• *%s* - $%.2f\n  _%s_\n\n", 
                            p.getName(), 
                            p.getPrice(),
                            p.getDescription()));
                }
                return sb.toString();
            })
            .onFailure().recoverWithItem(e -> {
                LOG.error("Product service failed", e);
                return "❌ Failed to load products. Please try again later.";
            })
            .subscribe().with(
                message -> telegram.sendMarkdown(chatId, message)
            );
    }

    /**
     * Handle /order <id> command - fetches order details from gRPC service.
     */
    private void handleOrder(String chatId, String orderId) {
        if (orderId.isEmpty()) {
            telegram.send(chatId, "⚠️ Please provide an order ID: `/order <id>`");
            return;
        }

        telegram.send(chatId, "🔍 Looking up order #" + orderId + "...");

        orderService.getOrder(orderId)
            .ifNoItem().after(TIMEOUT).fail()
            .map(order -> String.format("""
                📋 *Order #%s*
                
                📦 Status: %s
                💰 Total: $%.2f
                📅 Created: %s
                
                *Items:*
                %s
                """,
                order.getId(),
                formatStatus(order.getStatus()),
                order.getTotal(),
                order.getCreatedAt(),
                order.getItemsList().stream()
                    .map(item -> String.format("• %s x%d - $%.2f", 
                            item.getName(), 
                            item.getQuantity(), 
                            item.getPrice()))
                    .collect(Collectors.joining("\n"))
            ))
            .onFailure().recoverWithItem(e -> {
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.contains("NOT_FOUND")) {
                    return "❌ Order #" + orderId + " not found.";
                }
                LOG.error("Order lookup failed", e);
                return "❌ Failed to fetch order. Please try again.";
            })
            .subscribe().with(
                message -> telegram.sendMarkdown(chatId, message)
            );
    }

    /**
     * Handle /help command.
     */
    private void handleHelp(String chatId) {
        String helpText = """
            🤖 *Available Commands*
            
            /hello - Get a personalized greeting
            /products - List all available products
            /order <id> - Get order details by ID
            /help - Show this help message
            
            _Powered by Quarkus + gRPC_
            """;
        telegram.sendMarkdown(chatId, helpText);
    }

    private String formatStatus(String status) {
        return switch (status.toUpperCase()) {
            case "PENDING" -> "⏳ Pending";
            case "PROCESSING" -> "🔄 Processing";
            case "SHIPPED" -> "📦 Shipped";
            case "DELIVERED" -> "✅ Delivered";
            case "CANCELLED" -> "❌ Cancelled";
            default -> status;
        };
    }
}
```

#### 3. gRPC Service Wrapper (Returns Uni/Multi)

```java
package com.example.service;

import com.example.grpc.Greeter;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GreetingService {

    @GrpcClient("greeter")
    Greeter greeterClient;

    /**
     * Calls gRPC service and returns Uni.
     * The caller (Telegram handler) will subscribe to this Uni.
     */
    public Uni<String> getGreeting(String name) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .setLanguage("en")
                .build();

        return greeterClient.sayHello(request)
                .map(HelloReply::getMessage);
    }
}
```

```java
package com.example.service;

import com.example.grpc.Product;
import com.example.grpc.ProductServiceGrpc;
import com.example.grpc.ListProductsRequest;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductService {

    @GrpcClient("products")
    ProductServiceGrpc.ProductServiceStub productClient;

    /**
     * Calls gRPC streaming service and returns Multi.
     * The caller will collect or iterate over the stream.
     */
    public Multi<Product> listProducts() {
        ListProductsRequest request = ListProductsRequest.newBuilder()
                .build();

        return productClient.listProducts(request);
    }
}
```

### Key Points for Telegram Bot Integration

| Aspect | Recommendation |
|--------|----------------|
| **Consuming Uni** | Always use `.subscribe().with(onSuccess, onError)` |
| **Consuming Multi** | Use `.collect().asList()` then `.subscribe()`, or process each item |
| **Timeouts** | Always set with `.ifNoItem().after(Duration).fail()` |
| **Error Messages** | Use `.onFailure().recoverWithItem()` to provide user-friendly errors |
| **Loading Indicators** | Send a "Loading..." message before async calls |
| **Logging** | Log errors in error handlers for debugging |

### Common Mistakes to Avoid

```java
// ❌ WRONG: This does nothing! No subscription = no execution
greetingService.getGreeting(userName);  // Uni is never subscribed!

// ✅ CORRECT: Subscribe to trigger execution
greetingService.getGreeting(userName)
    .subscribe().with(greeting -> sendMessage(chatId, greeting));

// ❌ WRONG: Blocking in async context
String greeting = greetingService.getGreeting(userName)
    .await().indefinitely();  // Blocks the event loop!

// ✅ CORRECT: Stay non-blocking with subscribe
greetingService.getGreeting(userName)
    .subscribe().with(greeting -> sendMessage(chatId, greeting));

// ❌ WRONG: Ignoring errors
greetingService.getGreeting(userName)
    .subscribe().with(greeting -> sendMessage(chatId, greeting));
    // If error occurs, nothing happens - bad UX!

// ✅ CORRECT: Always handle errors
greetingService.getGreeting(userName)
    .subscribe().with(
        greeting -> sendMessage(chatId, greeting),
        error -> sendMessage(chatId, "Sorry, an error occurred!")
    );
```

---

## Testing Mutiny-based gRPC Clients

### Mocking gRPC Clients

```java
@QuarkusTest
class GreetingServiceTest {

    @InjectMock
    @GrpcClient("greeter")
    Greeter greeterClient;  // ✅ Mutiny interface can be mocked!

    @Inject
    GreetingService greetingService;

    @Test
    void testGreeting() {
        // Arrange
        HelloReply mockReply = HelloReply.newBuilder()
                .setMessage("Hello, Test!")
                .build();
        
        when(greeterClient.sayHello(any(HelloRequest.class)))
                .thenReturn(Uni.createFrom().item(mockReply));

        // Act
        String result = greetingService.getGreeting("Test")
                .await().atMost(Duration.ofSeconds(5));

        // Assert
        assertThat(result).isEqualTo("Hello, Test!");
    }
}
```

### Testing Error Scenarios

```java
@Test
void testGreetingError() {
    when(greeterClient.sayHello(any()))
            .thenReturn(Uni.createFrom().failure(
                new StatusRuntimeException(Status.UNAVAILABLE)));

    assertThatThrownBy(() -> 
            greetingService.getGreeting("Test")
                    .await().atMost(Duration.ofSeconds(5)))
            .isInstanceOf(StatusRuntimeException.class);
}
```

### Testing Streaming

```java
@Test
void testStreamingGreetings() {
    Multi<HelloReply> mockStream = Multi.createFrom().items(
            HelloReply.newBuilder().setMessage("Hello 1").build(),
            HelloReply.newBuilder().setMessage("Hello 2").build()
    );
    
    when(greeterClient.sayHelloStream(any()))
            .thenReturn(mockStream);

    List<String> results = greetingService.streamGreetings("Test")
            .collect().asList()
            .await().atMost(Duration.ofSeconds(5));

    assertThat(results).containsExactly("Hello 1", "Hello 2");
}
```

---

## Common Patterns and Operators

### Transformation

```java
// Map single value
uni.map(value -> transform(value))

// Chain async operations
uni.chain(value -> anotherAsyncCall(value))

// Flat map for Multi
multi.flatMap(item -> Multi.createFrom().items(item, item))
```

### Error Handling

```java
// Recover with default value
uni.onFailure().recoverWithItem("default")

// Recover with another Uni
uni.onFailure().recoverWithUni(this::fallbackService)

// Transform failure
uni.onFailure().transform(e -> new CustomException(e))

// Retry
uni.onFailure().retry().atMost(3)
```

### Timeout

```java
// Fail after timeout
uni.ifNoItem().after(Duration.ofSeconds(5)).fail()

// Use fallback after timeout
uni.ifNoItem().after(Duration.ofSeconds(5)).recoverWithItem("timeout fallback")
```

### Combining Multiple Calls

```java
// Sequential
Uni<C> result = uniA
    .chain(a -> uniB)
    .chain(b -> uniC);

// Parallel
Uni<Tuple3<A, B, C>> result = Uni.combine().all()
    .unis(uniA, uniB, uniC)
    .asTuple();

// Join with combinator
Uni<Result> result = Uni.combine().all()
    .unis(uniA, uniB)
    .with((a, b) -> new Result(a, b));
```

### Multi Operations

```java
// Collect to list
Multi<T> multi -> Uni<List<T>> via multi.collect().asList()

// First item only
Multi<T> multi -> Uni<T> via multi.toUni()

// Filter
multi.filter(item -> condition)

// Take first N
multi.select().first(10)

// Group
multi.group().by(Item::getCategory)
```

---

## Quick Reference

### When to Use What

| Situation | Pattern |
|-----------|---------|
| REST endpoint | Return `Uni`/`Multi` directly |
| Tests | Use `.await().atMost(Duration)` |
| Telegram bot | Use `.subscribe().with()` |
| CLI/Batch | Use `.await().indefinitely()` |
| Multiple sequential calls | Use `.chain()` |
| Multiple parallel calls | Use `Uni.combine().all()` |

### Subscribe Methods

| Method | Use Case |
|--------|----------|
| `.subscribe().with(onSuccess)` | Only care about success |
| `.subscribe().with(onSuccess, onError)` | Handle success and failure |
| `.subscribe().with(onItem, onError, onComplete)` | For Multi - each item, error, completion |

### Blocking Conversion Cheat Sheet

| Blocking Pattern | Mutiny Equivalent |
|-----------------|-------------------|
| `return stub.call(req)` | `return client.call(req)` (returns `Uni`) |
| `try { } catch { }` | `.onFailure().recoverWithItem()` |
| `for (item : iterator)` | `multi.subscribe().with()` |
| `Thread.sleep()` | `.onItem().delayIt().by()` |
| `result.get()` | `.await().atMost(Duration)` |

---

## Best Practices Summary

1. **Use Mutiny Service Interfaces** for gRPC clients (not blocking stubs) for easy testing

2. **Propagate Uni/Multi** through your application layers; don't block mid-chain

3. **Let Quarkus Subscribe** at REST endpoints - return `Uni`/`Multi` directly

4. **Use `.subscribe().with()`** in event handlers (Telegram, Kafka, etc.)

5. **Always Set Timeouts** for external calls: `.ifNoItem().after(Duration).fail()`

6. **Handle Errors Declaratively** using `.onFailure().recoverWithItem()` or `.onFailure().retry()`

7. **Use `.await()` Only In**:
   - Tests
   - CLI applications
   - Batch jobs
   - When integrating with blocking libraries

8. **Chain Operations** with `.chain()` for sequential calls, `Uni.combine()` for parallel

9. **Test with `@InjectMock`** - only works with Mutiny service interfaces

10. **Collect Streams** when needed: `multi.collect().asList()` → `Uni<List<T>>`

---

## Useful Links

- [SmallRye Mutiny Documentation](https://smallrye.io/smallrye-mutiny/)
- [Quarkus Reactive Guide](https://quarkus.io/guides/mutiny-primer)
- [Quarkus gRPC Guide](https://quarkus.io/guides/grpc-service-consumption)
- [Quarkus Testing Guide](https://quarkus.io/guides/getting-started-testing)
