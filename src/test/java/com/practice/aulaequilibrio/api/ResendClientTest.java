package com.practice.aulaequilibrio.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.aulaequilibrio.util.ValidationException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ResendClientTest {
    @Test void sendsThePdfAndReportsQuotaFailure() throws Exception {
        var request = new AtomicReference<String>();
        var token = new AtomicReference<String>();
        var idempotency = new AtomicReference<String>();
        var status = new AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/emails", exchange -> {
            try (exchange) {
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                token.set(exchange.getRequestHeaders().getFirst("Authorization"));
                idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                byte[] body = "{\"id\":\"resend-demo\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status.get(), body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try (var http = HttpClient.newHttpClient()) {
            var client = new ResendClient(http, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails"),
                    "clave-de-prueba", "onboarding@resend.dev");
            byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
            assertEquals("resend-demo", client.sendSchedule("docente@example.test", "Semana A", pdf));
            assertEquals("Bearer clave-de-prueba", token.get());
            assertNotNull(idempotency.get());
            var body = new ObjectMapper().readTree(request.get());
            assertEquals("docente@example.test", body.path("to").get(0).asText());
            assertArrayEquals(pdf, Base64.getDecoder().decode(body.path("attachments").get(0).path("content").asText()));
            status.set(429);
            assertThrows(ValidationException.class, () -> client.sendSchedule("docente@example.test", "Semana A", pdf));
        } finally {
            server.stop(0);
        }
    }
}
