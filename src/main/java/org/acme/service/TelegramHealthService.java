package org.acme.service;

import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.restclient.TelegramApiClient;
import org.acme.model.TelegramResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Objects;

/**
 * Service for checking Telegram API connectivity as part of application health monitoring.
 *
 * <p>This service provides a readiness check that verifies the bot can successfully
 * communicate with the Telegram Bot API. It is used by the
 * {@link org.acme.health.TelegramReadinessProbe} to determine if the application
 * is ready to accept traffic.</p>
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li><strong>CDI Bean</strong>: Annotated with {@code @ApplicationScoped} for singleton lifecycle management by Quarkus ArC.</li>
 *   <li><strong>REST Client Injection</strong>: Uses {@code @Inject} combined with {@code @RestClient} qualifier
 *       to inject the {@link TelegramApiClient} interface, which Quarkus implements at build time.</li>
 *   <li><strong>Configuration Injection</strong>: The bot token is injected via MicroProfile Config
 *       from the {@code camel.component.telegram.authorization-token} property, which maps to
 *       the {@code TELEGRAM_BOT_TOKEN} environment variable.</li>
 *   <li><strong>Caching</strong>: Results are cached for 10 minutes via {@code @CacheResult} to avoid
 *       excessive API calls during frequent health checks. Cache configuration is defined in
 *       {@code application.properties} under {@code quarkus.cache.caffeine.telegram-health-cache}.</li>
 * </ul>
 *
 * <h2>Configuration Properties</h2>
 * <pre>{@code
 * # Base URL for Telegram API (configured in application.properties)
 * quarkus.rest-client.telegram-api.url=https://api.telegram.org
 *
 * # Bot token from environment variable (via Camel component config)
 * camel.component.telegram.authorization-token=${TELEGRAM_BOT_TOKEN}
 *
 * # Cache expiration (configured in application.properties)
 * quarkus.cache.caffeine.telegram-health-cache.expire-after-write=PT10M
 * }</pre>
 *
 * <h2>Usage in Health Check</h2>
 * <pre>{@code
 * @Readiness
 * @ApplicationScoped
 * public class TelegramReadinessProbe implements HealthCheck {
 *
 *     @Inject
 *     TelegramHealthService telegramHealthService;
 *
 *     @Override
 *     public HealthCheckResponse call() {
 *         return HealthCheckResponse.builder()
 *             .name("telegram-bot-readiness-check")
 *             .status(telegramHealthService.isTelegramConnectivityOk())
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. The {@code @ApplicationScoped} scope ensures a single instance,
 * and the {@code @CacheResult} annotation handles concurrent access to the cached result.</p>
 *
 * @see org.acme.health.TelegramReadinessProbe
 * @see org.acme.restclient.TelegramApiClient
 * @see org.acme.model.TelegramResponse
 */

@ApplicationScoped
public class TelegramHealthService {

    @Inject
    @RestClient
    TelegramApiClient telegramApiClient;

    @ConfigProperty(name = "camel.component.telegram.authorization-token")
    String botToken;

    @CacheResult(cacheName = "telegram-health-cache")
    public boolean isTelegramConnectivityOk() {
        try {
            TelegramResponse response = telegramApiClient.getMe(botToken);
            Log.infof("✅ Telegram API was called during health check: %s", response);
            return Objects.nonNull(response) && response.ok();
        } catch (Exception e) {
            Log.error("❌ Failed to connect to Telegram API during health check", e);
            return false;
        }
    }
}
