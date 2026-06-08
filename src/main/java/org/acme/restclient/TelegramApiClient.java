package org.acme.restclient;

// ========================================================================
// Quarkus REST Client — Typed, Config-Driven HTTP Client (MicroProfile)
// ========================================================================
//
// A Quarkus REST Client is a **Java interface** annotated with JAX-RS
// annotations (@GET, @POST, @Path, etc.) that Quarkus implements at build
// time.  You never write an implementation class Quarkus generates one.
//
// Required Maven dependency in pom.xml:
//
//     <dependency>
//         <groupId>io.quarkus</groupId>
//         <artifactId>quarkus-rest-client-jackson</artifactId>
//     </dependency>
//
// (quarkus-rest-client-jackson adds JSON support via Jackson; use
//  quarkus-rest-client-jsonb if you prefer JSON-B.)
//
// ========================================================================

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.acme.model.TelegramResponse;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Typed REST client for the Telegram Bot API.
 *
 * <p>This interface declares the remote Telegram API calls this bot makes.
 * Each method maps to one Telegram endpoint.  The base URL
 * ({@code https://api.telegram.org}) is <strong>not</strong> hard-coded here
 * but is provided via {@code application.properties} using the
 * {@code configKey}:</p>
 *
 * <pre>{@code
 * quarkus.rest-client.telegram-api.url=https://api.telegram.org
 * }</pre>
 *
 * <p><b>How to use this client in a service:</b></p>
 * <pre>{@code
 * import org.eclipse.microprofile.rest.client.inject.RestClient;
 *
 * @ApplicationScoped
 * public class TelegramHealthService {
 *
 *     @Inject
 *     @RestClient                                        // <-- IMPORTANT: @RestClient qualifier
 *     TelegramApiClient telegramApiClient;
 *
 *     public boolean isTelegramConnectivityOk() {
 *         TelegramResponse response = telegramApiClient.getMe(botToken);
 *         return response.ok();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Key rules to remember:</b></p>
 * <ul>
 *   <li>The injection point <strong>must</strong> use both {@code @Inject}
 *       and {@code @RestClient} the interface type alone is ambiguous.</li>
 *   <li>Use {@code configKey} (not the fully-qualified class name) in
 *       {@code @RegisterRestClient} so that property names survive
 *       package renames.</li>
 *   <li>Every client method is synchronous by default.  Return
 *       {@code Uni<T>} for non-blocking or {@code RestResponse<T>} when
 *       you need HTTP status codes / headers alongside the payload.</li>
 * </ul>
 *
 * @see <a href="https://quarkus.io/guides/rest-client">Quarkus REST Client Guide</a>
 * @see org.acme.service.TelegramHealthService  Example injection point
 */
@RegisterRestClient(configKey = "telegram-api")
public interface TelegramApiClient {

    /**
     * Calls {@code GET /bot{token}/getMe} on the Telegram API.
     *
     * <p>The {@code {token}} placeholder is replaced at runtime by the
     * {@code @PathParam("token") String token} argument.  The actual
     * bot token comes from the {@code TELEGRAM_BOT_TOKEN} environment
     * variable (see {@code application.properties}).</p>
     *
     * <p>Telegram's {@code getMe} endpoint returns:</p>
     * <pre>{@code
     * { "ok": true, "result": { "id": 123456, ... } }
     * }</pre>
     *
     * <p>The response is deserialised into a {@link TelegramResponse} record.
     * Fields not present in {@code TelegramResponse} are silently ignored
     * thanks to {@code @JsonIgnoreProperties(ignoreUnknown = true)}.</p>
     *
     * @param token  the Telegram bot token (appended as a path segment)
     * @return       {@link TelegramResponse} containing the {@code ok} field
     */
    @GET
    @Path("/bot{token}/getMe")
    TelegramResponse getMe(@PathParam("token") String token);

    // ====================================================================
    // ADDITIONAL TELEGRAM API METHODS (add as needed)
    // ====================================================================
    //
    // @POST
    // @Path("/bot{token}/sendMessage")
    // TelegramResponse sendMessage(@PathParam("token") String token,
    //                              SendMessageRequest request);
    //
    // @POST
    // @Path("/bot{token}/sendPhoto")
    // TelegramResponse sendPhoto(@PathParam("token") String token,
    //                            SendPhotoRequest request);
    //
    // ====================================================================
}


// ========================================================================
// APPENDIX — ADVANCED QUARKUS REST CLIENT PATTERNS
// ========================================================================
//
// Below are additional concepts you can use when building other REST
// clients.  They are collected here as a quick-reference; none of them
// affect the TelegramApiClient above.
//
// ------------------------------------------------------------------------
// 1. QUERY PARAMETERS
// ------------------------------------------------------------------------
//
//    @Path("/extensions")
//    @RegisterRestClient(configKey = "extensions-api")
//    public interface ExtensionsService {
//
//        @GET
//        Set<Extension> getById(@QueryParam("id") String id);
//
//        // @RestQuery is a RESTEasy Reactive shorthand where the
//        // parameter name IS the query parameter name:
//        @GET
//        Set<Extension> getByName(@RestQuery String name);
//
//        // Also works with Map<String, String> and
//        // MultivaluedMap<String, String>:
//        @GET
//        Set<Extension> getByFilter(@RestQuery Map<String, String> filter);
//    }
//
// ------------------------------------------------------------------------
// 2. CUSTOM HEADERS
// ------------------------------------------------------------------------
//
//    @Path("/partners")
//    @RegisterRestClient(configKey = "partner-api")
//    @ClientHeaderParam(name = "X-Service-Name", value = "pricing-service")
//    @ClientHeaderParam(name = "Authorization", value = "{bearerToken}")
//    public interface PartnerClient {
//
//        @GET
//        PartnerStatus status();
//
//        // Method referenced via "{bearerToken}" — must match signature:
//        //   default String methodName(String headerName)
//        default String bearerToken(String headerName) {
//            return "Bearer " + Tokens.current();
//        }
//    }
//
//    - @ClientHeaderParam supports constants, config-property references
//      (${...}), and computed values via method references ({methodName}).
//    - Use @RegisterProvider(MyFilter.class) for more complex filter logic.
//
// ------------------------------------------------------------------------
// 3. REACTIVE RETURN TYPES (NON-BLOCKING)
// ------------------------------------------------------------------------
//
//    @Path("/quotes")
//    @RegisterRestClient(configKey = "quote-api")
//    public interface QuoteClient {
//
//        @GET
//        @Path("/{sku}")
//        Uni<Quote> quote(@PathParam("sku") String sku);
//    }
//
//    - Use Uni<T> for single async results, Multi<T> for streams.
//    - Keep the chain reactive — avoid .await().indefinitely()
//      in request paths.
//
// ------------------------------------------------------------------------
// 4. ACCESS HTTP STATUS & HEADERS (RestResponse<T>)
// ------------------------------------------------------------------------
//
//    @GET
//    RestResponse<Set<Extension>> getById(@QueryParam("id") String id);
//
//    - Use when callers need status codes or response headers.
//    - Disable the default exception mapper when using RestResponse
//      to avoid WebApplicationException for non-2xx statuses.
//
// ------------------------------------------------------------------------
// 5. EXCEPTION MAPPING (PER-CLIENT)
// ------------------------------------------------------------------------
//
//    @RegisterRestClient(configKey = "inventory-api")
//    public interface InventoryClient {
//
//        @GET
//        ItemAvailability get();
//
//        // Static method annotated with @ClientExceptionMapper is
//        // called for every non-2xx response:
//        @ClientExceptionMapper
//        static RuntimeException map404(Response response) {
//            if (response.getStatus() == 404)
//                return new UnknownItemException();
//            return null;  // fall through to default handling
//        }
//    }
//
//    - Keep mapping local to the client that needs it.
//    - Return null to let the default exception mapper handle the status.
//
// ------------------------------------------------------------------------
// 6. FAULT TOLERANCE (RETRY / CIRCUIT BREAKER)
// ------------------------------------------------------------------------
//
//    Put resilience annotations on the service method (business decision
//    layer), not blindly on every client method:
//
//    @ApplicationScoped
//    public class QuoteService {
//
//        @Inject
//        @RestClient
//        QuoteClient quotes;
//
//        @Retry(maxRetries = 2, delay = 200)
//        @CircuitBreaker(requestVolumeThreshold = 4,
//                        failureRatio = 0.5, delay = 1000)
//        @Fallback(fallbackMethod = "cachedQuote")
//        public Quote load(String sku) {
//            return quotes.quote(sku);
//        }
//
//        Quote cachedQuote(String sku) {
//            return Quote.unavailable(sku);
//        }
//    }
//
//    - Combine with Uni for reactive retry without MicroProfile Fault
//      Tolerance: .onFailure().retry().atMost(3)
//
// ------------------------------------------------------------------------
// 7. DYNAMIC URL OVERRIDE
// ------------------------------------------------------------------------
//
//    @RegisterRestClient(configKey = "tenant-api")
//    public interface TenantClient {
//
//        @GET
//        @Path("/health")
//        HealthStatus health(@Url URI baseUri);
//    }
//
//    - Use @Url for exceptional per-call routing.
//    - Prefer configured base URLs for normal service calls.
//
// ------------------------------------------------------------------------
// 8. MULTIPART UPLOADS
// ------------------------------------------------------------------------
//
//    @RegisterRestClient(configKey = "import-api")
//    public interface ImportClient {
//
//        @POST
//        @Consumes(MediaType.MULTIPART_FORM_DATA)
//        ImportResult upload(@RestForm("file") byte[] file,
//                            @RestForm @PartType(MediaType.APPLICATION_JSON)
//                            ImportMetadata metadata);
//    }
//
//    - Default to JSON for ordinary RPC calls; use multipart only when
//      binary payloads are truly part of the contract.
//
// ------------------------------------------------------------------------
// 9. KEY CONFIGURATION PROPERTIES (application.properties)
// ------------------------------------------------------------------------
//
//    # Base URL — required; every client needs one
//    quarkus.rest-client.<configKey>.url=https://api.example.com
//
//    # Timeouts (set both for external dependencies)
//    quarkus.rest-client.<configKey>.connect-timeout=2S
//    quarkus.rest-client.<configKey>.read-timeout=5S
//
//    # Redirects
//    quarkus.rest-client.<configKey>.follow-redirects=true
//    quarkus.rest-client.<configKey>.max-redirects=3
//
//    # Connection pool
//    quarkus.rest-client.<configKey>.connection-pool-size=50
//
//    # Scope
//    quarkus.rest-client.<configKey>.scope=jakarta.inject.Singleton
//
//    # Logging (DEBUG level, useful during troubleshooting)
//    quarkus.rest-client.logging.scope=request-response
//    quarkus.rest-client.logging.body-limit=1024
//    quarkus.log.category."org.jboss.resteasy.reactive.client.logging".level=DEBUG
//
//    # Profile-specific URLs (%dev, %prod, %test)
//    %dev.quarkus.rest-client.<configKey>.url=http://localhost:8089
//    %prod.quarkus.rest-client.<configKey>.url=https://prod.internal
//
// ------------------------------------------------------------------------
// 10. TESTING WITH WIREMOCK
// ------------------------------------------------------------------------
//
//     WireMock is the recommended approach for testing the real HTTP
//     boundary without calling the actual downstream service:
//
//     // 1. Test resource lifecycle manager
//     public class CustomerApiWireMock
//             implements QuarkusTestResourceLifecycleManager {
//         private static WireMockServer current;
//
//         public static WireMockServer server() {
//             return current;
//         }
//
//         @Override
//         public Map<String, String> start() {
//             var wm = new WireMockServer(options().dynamicPort());
//             wm.start();
//             current = wm;
//             return Map.of(
//                 "quarkus.rest-client.customer-api.url", wm.baseUrl());
//         }
//
//         @Override
//         public void stop() {
//             if (wm != null) wm.stop();
//             current = null;
//         }
//     }
//
//     // 2. Quarkus integration test
//     @QuarkusTest
//     @QuarkusTestResource(CustomerApiWireMock.class)
//     class CustomerClientTest {
//
//         @Inject
//         @RestClient
//         CustomerClient customers;
//
//         @Test
//         void getsCustomerFromStub() {
//             CustomerApiWireMock.server().stubFor(
//                 get(urlEqualTo("/customers/42"))
//                     .willReturn(okJson("""{"id":"42","name":"Ada"}""")));
//
//             assertEquals("Ada", customers.get("42").name());
//         }
//     }
//
// ========================================================================
