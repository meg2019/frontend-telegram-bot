package org.acme.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "telegram-api")
public interface TelegramApiClient {

    @GET
    @Path("/bot{token}/getMe")
    Response getMe(@PathParam("token") String token);
}