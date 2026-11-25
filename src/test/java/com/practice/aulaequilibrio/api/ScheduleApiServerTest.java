package com.practice.aulaequilibrio.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.aulaequilibrio.dao.DashboardDAOTest;
import com.practice.aulaequilibrio.dao.ScheduleDAO;
import com.practice.aulaequilibrio.dao.ScheduleDAOTest;
import com.practice.aulaequilibrio.dao.ScheduleReportDAO;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleApiServerTest {
    @Test void readsRealSchedulesAndRejectsInvalidRequests() throws Exception {
        try (var factory = DashboardDAOTest.createFactory();
             var api = new ScheduleApiServer(new ScheduleReportDAO(factory), "token-de-prueba", 0);
             var client = HttpClient.newHttpClient()) {
            var fixture = ScheduleDAOTest.fixture(factory);
            var dao = new ScheduleDAO(factory);
            int id = dao.saveVersion(null, "Semana API");
            dao.saveAssignment(id, null, fixture.placement(1, "08:00"));
            api.start();

            String versions = "http://127.0.0.1:" + api.port() + "/api/horarios/versiones";
            String sessions = versions + "/" + id + "/sesiones";
            var json = new ObjectMapper();
            var list = get(client, versions, true);
            assertEquals(200, list.statusCode());
            assertEquals(id, json.readTree(list.body()).get(0).path("id").asInt());

            var filtered = get(client, sessions + "?grupoId=" + fixture.group(), true);
            assertEquals(200, filtered.statusCode());
            var row = json.readTree(filtered.body()).path("sesiones").get(0);
            assertEquals("Lunes", row.path("dia").asText());
            assertEquals("08:00", row.path("inicio").asText());
            assertEquals("A1", row.path("aula").asText());
            assertTrue(json.readTree(get(client, sessions + "?docenteId=" + fixture.otherTeacher(), true).body())
                    .path("sesiones").isEmpty());
            assertEquals(401, get(client, versions, false).statusCode());
            assertEquals(400, get(client, sessions + "?grupoId=-1", true).statusCode());
            assertEquals(404, get(client, versions + "/99999/sesiones", true).statusCode());
        }
    }

    private static HttpResponse<String> get(HttpClient client, String url, boolean authorized) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url));
        if (authorized) request.header("Authorization", "Bearer token-de-prueba");
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
