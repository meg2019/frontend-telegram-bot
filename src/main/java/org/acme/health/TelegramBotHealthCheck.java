package org.acme.health;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HTTP;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@Readiness
@ApplicationScoped
public class TelegramBotHealthCheck implements HealthCheck {

    @Inject
    @RestClient
    TelegramApiClient telegramApiClient;

    @ConfigProperty(name = "camel.component.telegram.authorization-token")
    String botToken;

    @Override
    public HealthCheckResponse call() {
        if (StringUtils.isEmpty(botToken)) {
            Log.info("❌Bot token is not configured");
            return HealthCheckResponse.builder()
                    .name("telegram-bot")
                    .down()
                    .withData("error", "Bot token is not configured")
                    .build();
        }
        try {
            Response response = telegramApiClient.getMe(botToken);
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                return HealthCheckResponse.builder()
                        .name("telegram-bot")
                        .up()
                        .withData("status",
                                String.format("Bot is running, got answer with status: %s from Telegram API",
                                        response.getStatus()))
                        .build();
            } else {
                return HealthCheckResponse.builder()
                        .name("telegram-bot")
                        .down()
                        .withData("error", "Invalid bot token or API error")
                        .withData("status_code", response.getStatus())
                        .build();
            }
        } catch (Exception e) {
            return HealthCheckResponse.builder()
                    .name("telegram-bot")
                    .down()
                    .withData("error", String.format("Error connecting to Telegram API: %s", e.getMessage()))
                    .build();
        }
    }
}