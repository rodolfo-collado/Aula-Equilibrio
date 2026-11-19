package com.practice.aulaequilibrio.view;

import com.practice.aulaequilibrio.controller.HomeController;
import com.practice.aulaequilibrio.controller.LoginController;
import com.practice.aulaequilibrio.model.AccountData.Identity;
import com.practice.aulaequilibrio.service.AuthenticationService;
import com.practice.aulaequilibrio.util.*;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;

public final class ApplicationNavigator {
    private final Stage stage;
    private final AuthenticationService auth = new AuthenticationService();
    public ApplicationNavigator(Stage stage) { this.stage = stage; }
    public void showLogin() {
        try {
            FXMLLoader loader = loader("login-view.fxml"); Parent root = loader.load();
            loader.<LoginController>getController().setOnLogin(this::authenticated); setRoot(root);
        } catch (IOException error) { throw new IllegalStateException("No se pudo abrir el acceso.", error); }
    }
    private void authenticated(Identity identity) {
        SessionContext.install(identity);
        if (identity.mustChangePassword()) changePassword(); else showHome();
    }
    private void showHome() {
        try {
            FXMLLoader loader = loader("home-view.fxml"); Parent root = loader.load();
            HomeController controller = loader.getController(); controller.setOnLogout(this::logout); controller.setOnChangePassword(this::changePassword);
            setRoot(root);
        } catch (IOException error) { throw new IllegalStateException("No se pudo abrir el escritorio.", error); }
    }
    private void changePassword() {
        Identity current = SessionContext.current();
        PasswordField previous = new PasswordField(), next = new PasswordField(), confirm = new PasswordField();
        previous.setId("password-current"); next.setId("password-next"); confirm.setId("password-confirm");
        Label feedback = new Label(); feedback.setWrapText(true); feedback.getStyleClass().add("field-error");
        Button save = new Button("Guardar contraseña"), cancel = new Button(current.mustChangePassword() ? "Cerrar sesión" : "Volver al escritorio");
        save.getStyleClass().add("primary"); save.setId("password-save");
        VBox form = new VBox(11, title("Cambiar contraseña"), new Label(current.mustChangePassword()
                ? "Debes cambiar la contraseña temporal antes de entrar." : "Tus sesiones anteriores se cerrarán al guardar."),
                new Label("Contraseña actual"), previous, new Label("Nueva contraseña (12–128 caracteres)"), next,
                new Label("Confirmar nueva contraseña"), confirm, feedback, save, cancel);
        form.getStyleClass().add("login-card"); form.setMaxWidth(460); form.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane page = new StackPane(form); page.getStyleClass().add("login-page");
        cancel.setOnAction(event -> { if (current.mustChangePassword()) logout(); else showHome(); });
        save.setOnAction(event -> {
            if (!next.getText().equals(confirm.getText())) { feedback.setText("Las contraseñas no coinciden."); return; }
            char[] oldSecret = previous.getText().toCharArray(), newSecret = next.getText().toCharArray();
            previous.clear(); next.clear(); confirm.clear(); form.setDisable(true);
            Task<Identity> task = new Task<>() { @Override protected Identity call() { return auth.changePassword(current.token(), oldSecret, newSecret); } };
            task.setOnSucceeded(done -> authenticated(task.getValue()));
            task.setOnFailed(done -> { form.setDisable(false); feedback.setText(ErrorMessages.forUser(task.getException())); });
            start(task);
        });
        setRoot(page);
    }
    private void logout() {
        Identity previous = SessionContext.current();
        if (stage.getScene() != null) stage.getScene().getRoot().setDisable(true);
        Task<Void> task = new Task<>() { @Override protected Void call() { if (previous != null) auth.logout(previous.token()); return null; } };
        task.setOnSucceeded(event -> { SessionContext.clear(); showLogin(); });
        task.setOnFailed(event -> {
            if (stage.getScene() != null) stage.getScene().getRoot().setDisable(false);
            // Se conserva la sesión para poder reintentar su revocación.
            Parent root = stage.getScene().getRoot();
            var desktop = root.lookup(".mdi-desktop");
            if (desktop != null) Dialogs.error(desktop, "Cerrar sesión", task.getException());
            else if (root instanceof StackPane pane) {
                Label error = new Label("No se pudo cerrar la sesión. Revisa la conexión e inténtalo de nuevo.");
                error.getStyleClass().add("field-error"); error.setWrapText(true); pane.getChildren().add(error);
                StackPane.setAlignment(error, javafx.geometry.Pos.BOTTOM_CENTER);
            }
        });
        start(task);
    }
    private void setRoot(Parent root) {
        if (stage.getScene() == null) {
            Scene scene = new Scene(root, 1040, 740);
            scene.getStylesheets().add(getClass().getResource("/com/practice/aulaequilibrio/styles/aula.css").toExternalForm());
            stage.setScene(scene);
        } else stage.getScene().setRoot(root);
    }
    private static Label title(String text) { Label result = new Label(text); result.getStyleClass().add("page-title"); return result; }
    private static FXMLLoader loader(String file) { return new FXMLLoader(ApplicationNavigator.class.getResource("/com/practice/aulaequilibrio/views/" + file)); }
    private static void start(Task<?> task) { Thread worker = new Thread(task, "sesion-usuario"); worker.setDaemon(true); worker.start(); }
}
