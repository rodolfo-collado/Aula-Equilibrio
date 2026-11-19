package com.practice.aulaequilibrio;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.service.AuthenticationService;
import com.practice.aulaequilibrio.util.SessionContext;
import com.practice.aulaequilibrio.view.ApplicationNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

public class AulaEquilibrioApplication extends Application {
    @Override
    public void init() {
        try {
            HibernateUtil.verifyConnection();
            HibernateUtil.loadDemoData();
            new AuthenticationService().prepareRoles();
        } catch (RuntimeException exception) {
            HibernateUtil.shutdown();
            throw exception;
        }
    }

    @Override
    public void start(Stage stage) {
        new ApplicationNavigator(stage).showLogin();
        stage.setTitle("Aula Equilibrio");
        stage.setMinWidth(1000);
        stage.setMinHeight(720);
        stage.setMaximized(true);
        stage.show();
        stage.setFullScreenExitHint("Pulsa Esc para volver al modo ventana.");
        stage.setFullScreen(true);
    }

    @Override
    public void stop() {
        try {
            if (SessionContext.current() != null) new AuthenticationService().logout(SessionContext.current().token());
        } catch (RuntimeException error) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "No se pudo registrar el cierre de sesión.");
        } finally { SessionContext.clear(); HibernateUtil.shutdown(); }
    }
}
