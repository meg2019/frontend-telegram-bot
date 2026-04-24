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
            Log.infof("Telegram API was called during health check: %s", response);
            return Objects.nonNull(response) && response.ok();
        } catch (Exception e) {
            Log.error("Failed to connect to Telegram API during health check", e);
            return false;
        }
    }

}
