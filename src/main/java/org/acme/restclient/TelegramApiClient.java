package org.acme.restclient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.acme.model.TelegramResponse;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "telegram-api")
public interface TelegramApiClient {

    @GET
    @Path("/bot{token}/getMe")
    TelegramResponse getMe(@PathParam("token") String token);
}