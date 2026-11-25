package com.practice.aulaequilibrio.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.aulaequilibrio.util.ValidationException;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ResendClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final URI endpoint;
    private final String key;
    private final String from;

    public ResendClient() {
        this(Dotenv.configure().ignoreIfMissing().load());
    }

    private ResendClient(Dotenv env) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create("https://api.resend.com/emails"), env.get("RESEND_API_KEY", ""),
                env.get("RESEND_FROM", ""));
    }

    ResendClient(HttpClient http, URI endpoint, String key, String from) {
        this.http = http;
        this.endpoint = endpoint;
        this.key = key;
        this.from = from;
    }

    public void requireConfiguration() {
        if (key.isBlank() || from.isBlank()) {
            throw new ValidationException("Configura RESEND_API_KEY y RESEND_FROM en .env para enviar horarios.");
        }
    }

    public String sendSchedule(String recipient, String versionName, byte[] pdf) throws IOException, InterruptedException {
        requireConfiguration();
        String payload = JSON.writeValueAsString(Map.of(
                "from", from, "to", List.of(recipient),
                "subject", "Aula Equilibrio · Horario semanal · " + versionName,
                "text", "Adjunto encontrarás tu horario semanal de la versión «" + versionName + "».",
                "attachments", List.of(Map.of("filename", "horario-semanal.pdf",
                        "content", Base64.getEncoder().encodeToString(pdf)))));
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .header("User-Agent", "AulaEquilibrio/1.0")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();

        HttpResponse<String> answer;
        try {
            answer = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException connection) {
            // Reutiliza el mismo cuerpo y la misma clave si la confirmación se perdió en la red.
            answer = http.send(request, HttpResponse.BodyHandlers.ofString());
        }
        if (answer.statusCode() >= 200 && answer.statusCode() < 300) {
            String id = JSON.readTree(answer.body()).path("id").asText();
            if (!id.isBlank()) return id;
            throw new IOException("Resend respondió sin identificador de envío.");
        }
        throw new ValidationException(switch (answer.statusCode()) {
            case 401, 403 -> "Resend rechazó el acceso. Revisa la clave, el remitente y los permisos del dominio.";
            case 429 -> "Resend alcanzó un límite de envío. Revisa la cuota antes de intentar otra vez.";
            default -> "Resend rechazó el envío (HTTP " + answer.statusCode() + ").";
        });
    }
}
