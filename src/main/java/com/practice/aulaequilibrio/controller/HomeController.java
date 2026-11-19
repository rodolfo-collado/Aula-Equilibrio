package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.util.Dialogs;
import com.practice.aulaequilibrio.model.DashboardData.Destination;
import com.practice.aulaequilibrio.view.ScheduleView;
import com.practice.aulaequilibrio.view.mdi.MdiDesktop;
import com.practice.aulaequilibrio.view.AccountManagementView;
import com.practice.aulaequilibrio.model.Permission;
import com.practice.aulaequilibrio.util.SessionContext;
import com.practice.aulaequilibrio.util.UiAccess;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.io.IOException;

public class HomeController {
    private static final System.Logger LOGGER = System.getLogger(HomeController.class.getName());
    private static final String DASHBOARD = "Dashboard";
    private static final PseudoClass MODULE_OPEN = PseudoClass.getPseudoClass("module-open");
    private static final PseudoClass MODULE_ACTIVE = PseudoClass.getPseudoClass("module-active");

    @FXML
    private Parent home;
    @FXML
    private DashboardController homeController;
    @FXML
    private MdiDesktop desktop;
    @FXML
    private ToolBar taskbar;
    @FXML
    private HBox popupTasks;
    @FXML private Label sessionLabel;
    @FXML private MenuItem accountsMenu, auditMenu;
    private Runnable onLogout = () -> {}, onChangePassword = () -> {};
    public void setOnLogout(Runnable callback) { onLogout = callback; }
    public void setOnChangePassword(Runnable callback) { onChangePassword = callback; }

    @FXML
    private void initialize() {
        var identity = SessionContext.current();
        sessionLabel.setText(identity == null ? "Sin sesión" : identity.displayName() + " · " + identity.role());
        accountsMenu.setVisible(UiAccess.can(Permission.MANAGE_ACCOUNTS));
        auditMenu.setVisible(UiAccess.can(Permission.VIEW_AUDIT));
        taskbar.disableProperty().bind(desktop.dialogShowingProperty());
        homeController.setNavigator(this::openDestination);
        desktop.activeTitleProperty().addListener((observable, previous, current) -> {
            updateTaskbar();
            if (DASHBOARD.equals(current)) homeController.refresh();
        });
        desktop.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> updateTaskbar());
        desktop.open(DASHBOARD, home, 1000, 660, false);
    }

    private void updateTaskbar() {
        String active = desktop.activeTitleProperty().get();
        for (Node item : taskbar.getItems()) {
            if (item instanceof Button button) {
                updateTask(button, active);
            }
        }
        popupTasks.getChildren().removeIf(node -> !desktop.isOpen(((Button) node).getText()));
        for (String title : desktop.openTitles()) {
            boolean inTaskbar = taskbar.getItems().stream()
                    .anyMatch(node -> node instanceof Button button && title.equals(button.getText()));
            if (!inTaskbar && popupTasks.getChildren().stream().noneMatch(node -> title.equals(((Button) node).getText()))) {
                Button task = new Button(title);
                task.setOnAction(event -> desktop.toggleTask(title));
                popupTasks.getChildren().add(task);
            }
        }
        for (Node item : popupTasks.getChildren()) {
            updateTask((Button) item, active);
        }
    }

    private void updateTask(Button button, String active) {
        String title = button.getText();
        button.pseudoClassStateChanged(MODULE_OPEN, desktop.isOpen(title));
        button.pseudoClassStateChanged(MODULE_ACTIVE, title.equals(active));
    }

    @FXML
    private void showHome(ActionEvent event) {
        activateExisting(event, DASHBOARD);
    }

    @FXML
    private void arrangeWindows() {
        desktop.cascade();
    }

    @FXML private void logout() { onLogout.run(); }
    @FXML private void changePassword() { onChangePassword.run(); }
    @FXML private void openAccounts() { openAccountWindow(true); }
    @FXML private void openAudit() { openAccountWindow(false); }
    private void openAccountWindow(boolean management) {
        String title = management ? "Cuentas y permisos" : "Auditoría de cuentas";
        if (!UiAccess.can(management ? Permission.MANAGE_ACCOUNTS : Permission.VIEW_AUDIT)) return;
        if (desktop.activate(title)) return;
        AccountManagementView view = new AccountManagementView(management);
        desktop.open(title, view, 1100, 700, true); view.load();
    }

    @FXML
    private void exitApplication() {
        Platform.exit();
    }

    @FXML
    private void openCatalog(ActionEvent event) {
        Button shortcut = (Button) event.getSource();
        String title = shortcut.getAccessibleText();
        if (activateExisting(event, title)) {
            return;
        }
        openCatalog(title, shortcut.getUserData().toString());
    }

    private void openCatalog(String title, String file) {
        if (desktop.activate(title)) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/practice/aulaequilibrio/views/" + file));
            Parent catalog = loader.load();
            if (loader.getController() instanceof CatalogController controller) {
                controller.attachDesktop(desktop, title);
            }
            desktop.open(title, catalog, 1000, 660, true);
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "No se pudo abrir " + title, exception);
            Dialogs.error(desktop, "No se pudo abrir " + title, exception);
        }
    }

    @FXML
    private void openSchedules(ActionEvent event) {
        if (activateExisting(event, "Horarios")) {
            return;
        }
        openSchedules();
    }

    private void openSchedules() {
        if (desktop.activate("Horarios")) return;
        ScheduleView schedules = new ScheduleView();
        schedules.load();
        desktop.open("Horarios", schedules, 900, 540, true);
    }

    private void openDestination(Destination destination) {
        switch (destination) {
            case FACULTIES -> openCatalog("Facultades y carreras", "academic-structure-view.fxml");
            case TEACHERS -> openCatalog("Docentes y disponibilidad", "catalog-view.fxml");
            case OFFER -> openCatalog("Oferta académica", "academic-offer-view.fxml");
            case ROOMS -> openCatalog("Aulas", "classroom-view.fxml");
            case SCHEDULES -> openSchedules();
        }
    }

    private boolean activateExisting(ActionEvent event, String title) {
        if (taskbar.getItems().contains(event.getSource())) {
            return desktop.toggleTask(title);
        }
        return desktop.activate(title);
    }
}
