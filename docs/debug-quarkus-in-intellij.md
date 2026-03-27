# Debugging Quarkus Applications with IntelliJ IDEA

A comprehensive step-by-step guide to effectively debug Quarkus applications using IntelliJ IDEA.

## Prerequisites

1. **IntelliJ IDEA**: Ultimate or Community edition (Ultimate recommended for advanced features)
2. **Quarkus Project**: Your project should be properly configured with Quarkus framework
3. **Maven/Gradle**: Build tool configured (this project uses Maven)
4. **Java Development Kit (JDK)**: JDK 11 or higher (this project uses JDK 21)

## Project Structure Overview (for Reference)

This guide is tailored for the `frontend-telegram-bot` project, which includes:
- Quarkus 3.30.8 with Camel Telegram integration
- gRPC communication with backend services
- Camel routing and message processing
- Session management and quiz functionality

## Step 1: Configure the Quarkus Dev Service

Quarkus provides a development mode with hot reload and debugging capabilities.

### Option 1: Using Quarkus Dev Mode from Terminal

```bash
# Navigate to project root
cd /Users/megazoid/Documents/QuarkusProject/frontend-telegram-bot

# Start Quarkus in dev mode with debugging enabled (default port 5005)
./mvnw quarkus:dev
```

### Option 2: Configure Maven Run Configuration in IntelliJ

1. Open **Run > Edit Configurations**
2. Click **+** and select **Maven**
3. Configure:
   - **Name**: `Quarkus Dev Mode`
   - **Working directory**: `/Users/megazoid/Documents/QuarkusProject/frontend-telegram-bot`
   - **Command line**: `quarkus:dev`
4. Click **OK**

## Step 2: Create a Remote Debug Configuration

To attach the IntelliJ debugger to the running Quarkus application.

1. Open **Run > Edit Configurations**
2. Click **+** and select **Remote JVM Debug**
3. Configure:
   - **Name**: `Quarkus Debug`
   - **Debugger mode**: `Attach to remote JVM`
   - **Transport**: `Socket`
   - **Host**: `localhost`
   - **Port**: `5005` (Quarkus default debugging port)
4. Click **OK**

## Step 3: Setting Breakpoints

Set breakpoints at key locations in your code to pause execution and inspect state.

### Key Files to Debug in This Project

#### 1. Telegram Bot Route
- File: [`TelegramBotRoute.java`](../src/main/java/org/acme/route/TelegramBotRoute.java)
- Breakpoint locations:
  - Route initialization
  - Message handler endpoints
  - Error handling routes

#### 2. Command Processors
- File: [`StartCommandProcessor.java`](../src/main/java/org/acme/processor/StartCommandProcessor.java)
- File: [`StatusCommandProcessor.java`](../src/main/java/org/acme/processor/StatusCommandProcessor.java)
- File: [`AnswerProcessor.java`](../src/main/java/org/acme/processor/AnswerProcessor.java)
- File: [`CallbackQueryProcessor.java`](../src/main/java/org/acme/processor/CallbackQueryProcessor.java)
- File: [`ResultProcessor.java`](src/main/java/org/acme/processor/ResultProcessor.java)

#### 3. Services
- File: [`QuizWordService.java`](../src/main/java/org/acme/service/QuizWordService.java) - gRPC communication
- File: [`SessionManagerService.java`](../src/main/java/org/acme/service/SessionManagerService.java) - Session handling
- File: [`MenuService.java`](../src/main/java/org/acme/service/MenuService.java) - Menu creation
- File: [`QuizBotHandler.java`](src/main/java/org/acme/service/QuizBotHandler.java) - Bot operations

#### 4. Models
- File: [`UserQuizSession.java`](../src/main/java/org/acme/model/UserQuizSession.java) - Quiz session management

### How to Set Breakpoints

1. Navigate to the desired line of code
2. Click in the gutter (left margin) next to the line number
3. A red dot will appear indicating the breakpoint is active
4. Right-click the breakpoint for advanced options (condition, logging, etc.)

## Step 4: Start Debugging

1. **Start Quarkus Dev Mode**:
   - Run the `Quarkus Dev Mode` configuration
   - Wait for the application to start (look for `Listening for transport dt_socket at address: 5005`)

2. **Attach Debugger**:
   - Run the `Quarkus Debug` configuration
   - A debug panel will appear at the bottom of IntelliJ
   - You should see "Connected to the target VM" in the console

## Step 5: Debugging Features and Techniques

### Inspecting Variables

- **Variables Panel**: Shows current variables and their values
- **Watches**: Add expressions to watch specific values
- **Evaluate Expression**: Right-click a variable or select `Run > Evaluate Expression` to compute values

### Control Flow

- **Step Over (F8)**: Execute current line and move to next line
- **Step Into (F7)**: Enter the method being called
- **Step Out (Shift+F8)**: Exit the current method
- **Resume Program (F9)**: Continue execution until next breakpoint
- **Pause Program**: Pause execution at any time to inspect state

### Advanced Breakpoint Options

#### Conditional Breakpoints

1. Right-click breakpoint
2. Select **Edit Breakpoint**
3. Check **Condition**
4. Enter a boolean expression (e.g., `userSession.getScore() > 10`)
5. Click **Done**

#### Log Breakpoints

1. Right-click breakpoint
2. Select **Edit Breakpoint**
3. Check **Log message to console**
4. Enter message with variables (e.g., `User ${userSession.getUserId()} scored ${userSession.getScore()}`)
5. Check **Evaluate and log** if needed
6. Click **Done**

## Step 6: Debugging Camel Routes

### Camel Debugger Integration

1. Ensure you have the Camel debugger enabled
2. In `application.properties`, add:
   ```properties
   camel.debug.enabled = true
   ```

### Setting Breakpoints in Camel Routes

- **Route Definitions**: Set breakpoints in route configuration
- **Message Processors**: Breakpoints in Processor implementations
- **Route Builders**: Debug route assembly

### Examining Camel Exchanges

When debugging Camel routes, inspect the `Exchange` object:
- `exchange.getIn()` - Incoming message
- `exchange.getOut()` - Outgoing message
- `exchange.getException()` - Any exceptions thrown

## Step 7: Debugging gRPC Communication

### gRPC Services and Stubs

This project uses gRPC for backend communication. Key files:
- File: [`WordServiceGrpc.java`](../target/generated-sources/grpc/org/acme/backend/model/WordServiceGrpc.java)
- File: [`WordServiceClient.java`](../target/generated-sources/grpc/org/acme/backend/model/WordServiceClient.java)
- File: [`QuizWordService.java`](../src/main/java/org/acme/service/QuizWordService.java) - gRPC client implementation

### Debugging gRPC Calls

1. Set breakpoints in `QuizWordService.java` where gRPC methods are called
2. Examine request and response objects
3. Check for errors in gRPC communication
4. Monitor request/response timings

## Step 8: Analyzing Exceptions

### Exception Breakpoints

1. Go to **Run > View Breakpoints** (Ctrl+Shift+F8)
2. Click **+** and select **Java Exception Breakpoints**
3. Enter exception class name (e.g., `io.grpc.StatusRuntimeException`)
4. Click **OK**

### Stack Trace Analysis

- When an exception is thrown, the debug panel shows the stack trace
- Click on stack trace elements to navigate to problematic code
- Check variable values at each stack frame

## Step 9: Performance Debugging

### Profiling with IntelliJ

1. Run your application with profiling enabled
2. Go to **Run > Run with Profiler**
3. Select profiling type (CPU, Memory, etc.)
4. Analyze results in the profiler tab

### Debugging Performance Issues

- Identify long-running methods using call tree analysis
- Find memory leaks using memory profiling
- Monitor thread activity with thread dumps

## Step 10: Remote Debugging (Optional)

### Debugging in Kubernetes/OpenShift

1. Deploy Quarkus application with debugging enabled
2. Forward the debugging port to your local machine
3. Attach IntelliJ debugger as described earlier

### Kubernetes Port Forwarding Example

```bash
kubectl port-forward pod/<pod-name> 5005:5005
```

## Tips and Best Practices

### 1. Use Hot Reload

Quarkus dev mode supports hot reload:
- Make code changes while application is running
- Changes are automatically recompiled and reloaded
- No need to restart the application

### 2. Debug Configuration Shortcuts

- **Toggle Breakpoint**: Ctrl+F8
- **Run to Cursor**: Alt+F9
- **Quick Evaluate Expression**: Alt+F8

### 3. Logging Configuration

For better debugging, configure detailed logging in `application.properties`:

```properties
# Enable debug logging for specific packages
quarkus.log.category."org.acme".level = DEBUG
quarkus.log.category."org.apache.camel".level = DEBUG
quarkus.log.category."io.quarkus".level = INFO

# Console output format
quarkus.log.console.format = "%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n"
```

### 4. Debugging Tests

Debug Quarkus tests in IntelliJ:
1. Open the test file (e.g., `UserQuizSessionTest.java`)
2. Click the debug icon next to the test method
3. Tests will run in debug mode with your breakpoints

## Common Debugging Scenarios

### Scenario 1: Telegram Command Not Responding

1. Set breakpoint in [`TelegramBotRoute.java`](../src/main/java/org/acme/route/TelegramBotRoute.java) route initialization
2. Send a command from Telegram
3. Check if the route receives the message
4. Trace through the command processor chain

### Scenario 2: Quiz Session Management Issues

1. Set breakpoint in [`SessionManagerService.java`](../src/main/java/org/acme/service/SessionManagerService.java)
2. Track session creation and retrieval
3. Check session state changes during quiz progress
4. Verify session cleanup logic

### Scenario 3: gRPC Communication Failure

1. Set breakpoint in [`QuizWordService.java`](../src/main/java/org/acme/service/QuizWordService.java)
2. Check if gRPC stub is properly initialized
3. Verify request parameters
4. Examine gRPC response or error details

### Scenario 4: Camel Route Processing Errors

1. Set breakpoint in route error handler
2. Check exchange exception details
3. Verify message transformation steps
4. Analyze Camel exchange properties

## Resources

- [Quarkus Debugging Guide](https://quarkus.io/guides/debugging)
- [IntelliJ Debugging Documentation](https://www.jetbrains.com/help/idea/debugging-code.html)
- [Camel Debugging Guide](https://camel.apache.org/manual/debugger.html)
- [gRPC Java Documentation](https://grpc.io/docs/languages/java/)

## Troubleshooting

### Problem: Debugger Won't Attach

- Verify Quarkus is running in dev mode
- Check if port 5005 is available
- Ensure firewall isn't blocking the port
- Restart Quarkus dev mode

### Problem: Breakpoints Not Hit

- Check if the code is actually being executed
- Verify the application has reloaded after changes
- Ensure breakpoints are not disabled
- Check for conditional breakpoints that aren't matching

### Problem: Application Runs Slowly in Debug

- Reduce number of active breakpoints
- Avoid conditional breakpoints with complex logic
- Disable logging breakpoints when not needed
- Consider using production mode for performance testing

---

**Happy Debugging!** 🐛
