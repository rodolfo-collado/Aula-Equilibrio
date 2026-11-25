package com.practice.aulaequilibrio.api;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.dao.ScheduleReportDAO;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.concurrent.CountDownLatch;

public final class ScheduleApiLauncher {
    private ScheduleApiLauncher() {}

    public static void main(String[] args) throws Exception {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String token = env.get("AULA_API_TOKEN", "");
        if (token.isBlank()) throw new IllegalArgumentException("Configura AULA_API_TOKEN en .env.");
        int port = Integer.parseInt(env.get("AULA_API_PORT", "8080"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("AULA_API_PORT está fuera de rango.");

        var stopped = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        try (var factory = HibernateUtil.openReadOnlyFactory();
             var api = new ScheduleApiServer(new ScheduleReportDAO(factory), token, port)) {
            Thread shutdown = new Thread(() -> {
                stopped.countDown();
                // Espera el cierre del servidor y Hibernate durante Ctrl+C.
                try { finished.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "cerrar-api-horarios");
            Runtime.getRuntime().addShutdownHook(shutdown);
            api.start();
            System.out.printf("API de horarios: http://127.0.0.1:%d/api/horarios/versiones%n", api.port());
            stopped.await();
        } finally {
            finished.countDown();
        }
    }
}
