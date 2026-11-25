package com.practice.aulaequilibrio.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.aulaequilibrio.dao.ScheduleReportDAO;
import com.practice.aulaequilibrio.model.ScheduleReportData.SessionRow;
import com.practice.aulaequilibrio.service.ScheduleReportService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

final class ScheduleApiServer implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(ScheduleApiServer.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VERSIONS = "/api/horarios/versiones";
    private static final Pattern SESSIONS = Pattern.compile(VERSIONS + "/([0-9]+)/sesiones");
    private static final Set<String> FILTERS = Set.of("grupoId", "docenteId", "aulaId");

    private final ScheduleReportDAO reports;
    private final byte[] authorization;
    private final HttpServer server;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    ScheduleApiServer(ScheduleReportDAO reports, String token, int port) throws IOException {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Configura AULA_API_TOKEN.");
        this.reports = reports;
        this.authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        // Servicio local para demostración. Las consultas se autorizan con una clave de solo lectura.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        server.createContext("/api/horarios", this::handle);
        server.setExecutor(workers);
    }

    void start() { server.start(); }

    int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange request) throws IOException {
        try (request) {
            String token = request.getRequestHeaders().getFirst("Authorization");
            if (token == null || !MessageDigest.isEqual(authorization, token.getBytes(StandardCharsets.UTF_8))) {
                request.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                error(request, 401, "Se requiere un token Bearer válido.");
                return;
            }
            if (!"GET".equals(request.getRequestMethod())) {
                request.getResponseHeaders().set("Allow", "GET");
                error(request, 405, "Método no permitido.");
                return;
            }
            try {
                route(request);
            } catch (IllegalArgumentException invalid) {
                error(request, 400, "Indica identificadores enteros positivos y filtros válidos.");
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.ERROR, "Error consultando el horario", failure);
                error(request, 500, "No se pudo consultar el horario.");
            }
        }
    }

    private void route(HttpExchange request) throws IOException {
        String path = request.getRequestURI().getPath();
        String query = request.getRequestURI().getRawQuery();
        if (VERSIONS.equals(path)) {
            if (query != null && !query.isEmpty()) throw new IllegalArgumentException();
            var versions = reports.versions().stream().map(version -> Map.of(
                    "id", version.id(), "nombre", version.name(), "creadaEl", version.createdOn().toString())).toList();
            respond(request, 200, versions);
            return;
        }
        var matcher = SESSIONS.matcher(path);
        if (!matcher.matches()) {
            error(request, 404, "Ruta no encontrada.");
            return;
        }
        int id = positiveId(matcher.group(1));
        var filters = filters(query);
        if (!reports.versionExists(id)) {
            error(request, 404, "La versión de horario no existe.");
            return;
        }
        var sessions = reports.sessions(id).stream()
                .filter(row -> matches(filters.get("grupoId"), row.groupId())
                        && matches(filters.get("docenteId"), row.teacherId())
                        && matches(filters.get("aulaId"), row.classroomId()))
                .map(ScheduleApiServer::serialize).toList();
        respond(request, 200, Map.of("versionId", id, "sesiones", sessions));
    }

    private static Map<String, Integer> filters(String query) {
        Map<String, Integer> values = new HashMap<>();
        if (query == null || query.isEmpty()) return values;
        for (String pair : query.split("&", -1)) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2 || !FILTERS.contains(parts[0]) || values.containsKey(parts[0])) {
                throw new IllegalArgumentException();
            }
            values.put(parts[0], positiveId(URLDecoder.decode(parts[1], StandardCharsets.UTF_8)));
        }
        return values;
    }

    private static int positiveId(String value) {
        int id = Integer.parseInt(value);
        if (id < 1) throw new IllegalArgumentException();
        return id;
    }

    private static boolean matches(Integer filter, Integer id) {
        return filter == null || filter.equals(id);
    }

    private static Map<String, Object> serialize(SessionRow row) {
        return Map.of("dia", ScheduleReportService.dayName(row.day()), "inicio", row.start().toString(),
                "fin", row.end().toString(), "asignatura", row.subject(),
                "grupoId", row.groupId(), "grupo", row.group(),
                "docenteId", row.teacherId(), "docente", row.teacher(),
                "aulaId", row.classroomId(), "aula", row.classroom());
    }

    private static void error(HttpExchange request, int status, String message) throws IOException {
        respond(request, status, Map.of("error", message));
    }

    private static void respond(HttpExchange request, int status, Object data) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(data);
        request.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        request.getResponseHeaders().set("Cache-Control", "no-store");
        request.sendResponseHeaders(status, bytes.length);
        request.getResponseBody().write(bytes);
    }

    @Override public void close() {
        server.stop(0);
        workers.close();
    }
}
