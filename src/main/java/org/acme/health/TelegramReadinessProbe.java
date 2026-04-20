package org.acme.health;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.service.TelegramHealthService;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

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
