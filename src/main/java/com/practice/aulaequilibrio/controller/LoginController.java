package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.model.AccountData.Identity;
import com.practice.aulaequilibrio.service.AuthenticationService;
import com.practice.aulaequilibrio.util.ErrorMessages;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;

public class LoginController {
    @FXML private TextField username, displayName, email;
    @FXML private PasswordField password, confirm;
    @FXML private VBox setupFields;
    @FXML private Label title, subtitle, feedback;
    @FXML private Button submit;
    private final AuthenticationService service;
    private Consumer<Identity> onLogin = identity -> {};
    private boolean setup;
    public LoginController() { this(new AuthenticationService()); }
    public LoginController(AuthenticationService service) { this.service = service; }
    public void setOnLogin(Consumer<Identity> callback) { onLogin = callback; }
    @FXML private void initialize() {
        setupFields.managedProperty().bind(setupFields.visibleProperty());
        submit.setDisable(true);
        Task<Boolean> state = new Task<>() { @Override protected Boolean call() { return service.needsSetup(); } };
        state.setOnSucceeded(event -> {
            setup = state.getValue(); setupFields.setVisible(setup); submit.setDisable(false);
            title.setText(setup ? "Crea el primer administrador" : "Bienvenido a Aula Equilibrio");
            subtitle.setText(setup ? "Configura una cuenta propia para administrar el acceso. Contraseña de 12–128 caracteres."
                    : "Inicia sesión para acceder a la planificación académica.");
            submit.setText(setup ? "Crear administrador" : "Iniciar sesión");
        });
        state.setOnFailed(event -> feedback.setText(ErrorMessages.forUser(state.getException())));
        start(state);
    }
    @FXML private void login() {
        if (submit.isDisabled()) return;
        if (setup && !password.getText().equals(confirm.getText())) { feedback.setText("Las contraseñas no coinciden."); return; }
        String user = username.getText(), name = displayName.getText(), address = email.getText();
        char[] secret = password.getText().toCharArray(); password.clear(); confirm.clear();
        submit.setDisable(true); feedback.setText("Comprobando acceso…");
        Task<Identity> task = new Task<>() {
            @Override protected Identity call() { return setup ? service.createInitialAdmin(user, name, address, secret) : service.login(user, secret); }
        };
        task.setOnSucceeded(event -> { feedback.setText(""); submit.setDisable(false); onLogin.accept(task.getValue()); });
        task.setOnFailed(event -> { submit.setDisable(false); feedback.setText(ErrorMessages.forUser(task.getException())); });
        start(task);
    }
    private static void start(Task<?> task) { Thread worker = new Thread(task, "autenticacion"); worker.setDaemon(true); worker.start(); }
}
