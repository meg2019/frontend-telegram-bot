package org.acme.health;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.service.TelegramHealthService;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * MicroProfile Health readiness probe that verifies Telegram Bot API connectivity.
 *
 * <p>This check is part of the Quarkus SmallRye Health integration and is exposed
 * at {@code /q/health/ready}. It delegates to {@link TelegramHealthService} to
 * determine whether the application can successfully communicate with the Telegram
 * Bot API before accepting traffic.</p>
 *
 * <h2>Probe Type</h2>
 * <p>Annotated with {@code @Readiness}, this probe controls whether the application
 * is ready to handle requests. Kubernetes and other orchestrators use the readiness
 * endpoint to decide when to route traffic to the pod. If this probe returns
 * {@code DOWN}, the pod is removed from the service load balancer until the
 * dependency is restored.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>The class is annotated with {@code @ApplicationScoped}, meaning Quarkus ArC
 * creates a single instance for the entire application lifecycle. The
 * {@link TelegramHealthService} dependency is injected via constructor injection
 * using Lombok's {@code @RequiredArgsConstructor}.</p>
 *
 * <h2>Health Check Behavior</h2>
 * <p>The {@link #call()} method invokes {@link TelegramHealthService#isTelegramConnectivityOk()},
 * which performs a lightweight API call to the Telegram Bot API. The result is
 * cached for 10 minutes to avoid excessive remote calls during frequent health
 * check polling.</p>
 *
 * <h2>Response Details</h2>
 * <ul>
 *   <li><strong>Name</strong>: {@code telegram-bot-readiness-check}</li>
 *   <li><strong>Status</strong>: {@code UP} when the Telegram API responds successfully,
 *       {@code DOWN} otherwise.</li>
 *   <li><strong>Data</strong>: Includes a human-readable status message
 *       {@code "Telegram bot is ready"} when the check passes.</li>
 * </ul>
 *
 * @see TelegramHealthService
 * @see org.eclipse.microprofile.health.Readiness
 * @see org.eclipse.microprofile.health.HealthCheck
 */
@Readiness
@ApplicationScoped
@RequiredArgsConstructor
public class TelegramReadinessProbe implements HealthCheck {

    private final TelegramHealthService telegramHealthService;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.builder()
                .name("telegram-bot-readiness-check")
                .status(telegramHealthService.isTelegramConnectivityOk())
                .withData("status", "Telegram bot is ready")
                .build();
    }
}
