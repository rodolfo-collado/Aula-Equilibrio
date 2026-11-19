package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.dao.*;
import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.model.AccountData.*;
import com.practice.aulaequilibrio.service.*;
import com.practice.aulaequilibrio.util.SessionContext;
import com.practice.aulaequilibrio.view.*;
import com.practice.aulaequilibrio.view.mdi.MdiDesktop;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AuthenticationScreenTest {
    @Test void setupCreatesAdminAccountPanelCreatesAndAuditsUserAndReaderCannotEdit() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null);
        try { Platform.startup(() -> Platform.setImplicitExit(false)); } catch (IllegalStateException alreadyStarted) { }
        try (var factory = DashboardDAOTest.createFactory()) {
            AuthenticationDAO dao = new AuthenticationDAO(factory);
            AuthenticationService auth = new AuthenticationService(dao); auth.prepareRoles();
            AtomicReference<Identity> accepted = new AtomicReference<>();
            Parent login = fx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/practice/aulaequilibrio/views/login-view.fxml"));
                loader.setControllerFactory(type -> new LoginController(auth));
                Parent root = loader.load(); loader.<LoginController>getController().setOnLogin(accepted::set); return root;
            });
            Stage stage = fx(() -> { Stage window = new Stage(); window.setScene(new Scene(login, 1040, 740)); window.setMaximized(true); window.show(); return window; });
            try {
                waitFor(() -> !((Button)login.lookup("#submit")).isDisabled());
                fx(() -> {
                    ((TextField)login.lookup("#username")).setText("admin");
                    ((TextField)login.lookup("#displayName")).setText("Administración de pruebas");
                    ((PasswordField)login.lookup("#password")).setText("Test-ui-password-42");
                    ((PasswordField)login.lookup("#confirm")).setText("Test-ui-password-42");
                    ((Button)login.lookup("#submit")).fire(); return null;
                });
                waitFor(() -> accepted.get() != null);
                Identity admin = accepted.get(); SessionContext.install(admin);
                assertEquals(RoleCode.ADMINISTRADOR, admin.role());
                AccountService accounts = new AccountService(new AccountDAO(dao), new AccessControl(admin, dao));
                MdiDesktop desktop = fx(() -> new MdiDesktop());
                AccountManagementView view = fx(() -> {
                    AccountManagementView panel = new AccountManagementView(accounts, true);
                    stage.setScene(new Scene(desktop, 1100, 780)); desktop.open("Cuentas", panel, 1000, 700, true); panel.load(); return panel;
                });
                waitFor(() -> !view.isDisabled());
                fx(() -> {
                    click(view, "Nueva cuenta"); desktop.applyCss(); desktop.layout();
                    ((TextField)desktop.lookup("#account-username")).setText("reader");
                    ((TextField)desktop.lookup("#account-name")).setText("Usuario de consulta");
                    ((PasswordField)desktop.lookup("#account-password")).setText("Test-reader-password-42");
                    ((PasswordField)desktop.lookup("#account-confirm")).setText("Test-reader-password-42");
                    ((Button)((DialogPane)desktop.lookup(".mdi-dialog")).lookupButton(ButtonType.OK)).fire(); return null;
                });
                waitFor(() -> !view.isDisabled());
                assertEquals(2, accounts.list().size());
                fx(() -> {
                    ((TextField)view.lookup("#account-search")).setText("reader");
                    TableView<?> table = (TableView<?>)view.lookup("#account-table"); assertEquals(1, table.getItems().size());
                    table.getSelectionModel().selectFirst(); click(view, "Ver actividad"); return null;
                });
                waitFor(() -> !view.isDisabled());
                assertTrue(fx(() -> ((TableView<AuditRow>)view.lookup("#audit-table")).getItems().stream().anyMatch(e -> e.action() == AuditAction.ACCOUNT_CREATED)));
                Identity temporary = auth.login("reader", "Test-reader-password-42".toCharArray());
                Identity reader = auth.changePassword(temporary.token(), "Test-reader-password-42".toCharArray(), "Test-reader-new-password-42".toCharArray());
                SessionContext.install(reader); AccessControl access = new AccessControl(reader, dao);
                ScheduleView readOnly = fx(() -> {
                    var panel = new ScheduleView(new ScheduleService(new ScheduleDAO(factory), access));
                    desktop.close("Cuentas"); desktop.open("Horarios", panel, 1000, 700, true); panel.load(); return panel;
                });
                waitFor(() -> !readOnly.isDisabled());
                assertNull(fx(() -> readOnly.lookup("#session-save")));
                assertTrue(fx(() -> readOnly.lookupAll(".button").stream().noneMatch(n -> n instanceof Button b && "Nueva versión".equals(b.getText()))));
                Parent catalog = fx(() -> {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/practice/aulaequilibrio/views/catalog-view.fxml"));
                    loader.setControllerFactory(type -> new CatalogController(new CatalogService(new CatalogDAO(factory), access)));
                    Parent root = loader.load(); loader.<CatalogController>getController().attachDesktop(desktop, "Docentes");
                    desktop.open("Docentes", root, 1000, 700, true); return root;
                });
                waitFor(() -> !catalog.lookup("#catalogs").isDisabled());
                assertTrue(fx(() -> catalog.lookupAll(".button").stream().filter(n -> n instanceof Button b && "Nuevo docente".equals(b.getText())).allMatch(n -> !n.isVisible())));
            } finally { SessionContext.clear(); fx(() -> { stage.close(); return null; }); }
        }
    }
    private static void click(Parent root, String text) {
        root.lookupAll(".button").stream().filter(n -> n instanceof Button b && text.equals(b.getText())).map(Button.class::cast).findFirst().orElseThrow().fire();
    }
    private static void waitFor(Callable<Boolean> ready) throws Exception {
        for (int i = 0; i < 600; i++) { if (fx(ready)) return; Thread.sleep(25); }
        throw new AssertionError("La interfaz no terminó la operación.");
    }
    private static <T> T fx(Callable<T> action) throws Exception { var task = new FutureTask<>(action); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS); }
}
