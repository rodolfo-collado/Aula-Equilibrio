package com.practice.aulaequilibrio.view;

import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.model.AccountData.*;
import com.practice.aulaequilibrio.service.AccountService;
import com.practice.aulaequilibrio.util.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public class AccountManagementView extends BorderPane {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Managua"));
    private final AccountService service;
    private final boolean management;
    private final TableView<AccountRow> accounts = new TableView<>();
    private final TableView<AuditRow> events = new TableView<>();
    private final ObservableList<AccountRow> rows = FXCollections.observableArrayList();
    private final FilteredList<AccountRow> filtered = new FilteredList<>(rows);
    private final TextField search = new TextField(), auditSearch = new TextField();
    private final ComboBox<RoleCode> role = new ComboBox<>();
    private final ComboBox<String> state = new ComboBox<>();
    private final ComboBox<AuditAction> action = new ComboBox<>();
    private final Label status = new Label(), auditScope = new Label("Todas las cuentas");
    private final TabPane tabs = new TabPane();
    private final Tab auditTab = new Tab("Auditoría");
    private Long targetId;

    public AccountManagementView(boolean management) { this(new AccountService(), management); }
    public AccountManagementView(AccountService service, boolean management) {
        this.service = service; this.management = management;
        getStyleClass().add("module-page"); setId("account-management");
        Label title = label(management ? "Cuentas y permisos" : "Auditoría de cuentas", "page-title");
        Label description = label("Historial de inicios de sesión y cambios en las cuentas, sus roles y su estado.", "muted"); description.setWrapText(true);
        setTop(new VBox(6, title, description));
        accounts.setId("account-table"); events.setId("audit-table");
        accounts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        events.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        if (management) {
            Tab accountTab = new Tab("Usuarios", accountPanel()); accountTab.setClosable(false); tabs.getTabs().add(accountTab);
        }
        auditTab.setContent(auditPanel()); auditTab.setClosable(false); tabs.getTabs().add(auditTab);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, before, after) -> { if (after == auditTab) refreshAudit(); });
        setCenter(tabs); setBottom(status); status.setWrapText(true); status.getStyleClass().add("muted");
    }
    public void load() { if (management) refreshAccounts(); else refreshAudit(); }

    private VBox accountPanel() {
        search.setId("account-search"); search.setPromptText("Buscar usuario, nombre o correo"); search.setPrefWidth(250);
        role.getItems().add(null); role.getItems().addAll(RoleCode.values()); role.setPromptText("Todos los roles");
        state.getItems().setAll("Todas", "Activas", "Inactivas"); state.setValue("Todas");
        search.textProperty().addListener((obs, before, after) -> filter());
        role.valueProperty().addListener((obs, before, after) -> filter()); state.valueProperty().addListener((obs, before, after) -> filter());
        Button edit = button("Editar cuenta", () -> accountForm(accounts.getSelectionModel().getSelectedItem()));
        Button reset = button("Restablecer contraseña", this::resetPassword);
        Button unlock = button("Desbloquear", this::unlock);
        Button inspect = button("Ver actividad", () -> {
            AccountRow selected = accounts.getSelectionModel().getSelectedItem();
            if (selected != null) {
                targetId = selected.id(); auditScope.setText("Cuenta: " + selected.username());
                if (tabs.getSelectionModel().getSelectedItem() == auditTab) refreshAudit(); else tabs.getSelectionModel().select(auditTab);
            }
        });
        var noSelection = accounts.getSelectionModel().selectedItemProperty().isNull();
        for (Button button : new Button[]{edit, reset, unlock, inspect}) button.disableProperty().bind(noSelection);
        FlowPane actions = new FlowPane(7, 6, button("Nueva cuenta", () -> accountForm(null)), edit, reset, unlock, inspect, button("Actualizar", this::refreshAccounts));
        accounts.getColumns().add(column("Usuario", AccountRow::username));
        accounts.getColumns().add(column("Nombre", AccountRow::displayName));
        accounts.getColumns().add(column("Correo", a -> a.email() == null ? "—" : a.email()));
        accounts.getColumns().add(column("Rol", a -> a.role().toString()));
        accounts.getColumns().add(column("Estado", a -> !a.active() ? "Inactiva" : a.lockedUntil() != null && a.lockedUntil().isAfter(Instant.now()) ? "Bloqueada temporalmente" : "Activa"));
        accounts.getColumns().add(column("Acceso", a -> a.mustChangePassword() ? "Cambio de contraseña pendiente" : "Normal"));
        accounts.getColumns().add(column("Último acceso", a -> date(a.lastLogin())));
        accounts.setPlaceholder(new Label("No hay cuentas para estos filtros."));
        SortedList<AccountRow> sorted = new SortedList<>(filtered); sorted.comparatorProperty().bind(accounts.comparatorProperty()); accounts.setItems(sorted);
        Label roles = label("Administrador: cuentas y toda la gestión · Coordinador: catálogos y horarios · Planificador: horarios · Consulta: lectura · Auditor: lectura e historial.", "muted"); roles.setWrapText(true);
        VBox panel = new VBox(10, actions, new FlowPane(7, 6, search, role, state), roles, accounts);
        panel.getStyleClass().add("panel"); VBox.setVgrow(accounts, Priority.ALWAYS); return panel;
    }
    private VBox auditPanel() {
        action.getItems().add(null); action.getItems().addAll(AuditAction.values()); action.setPromptText("Todas las acciones");
        auditSearch.setPromptText("Actor o cuenta"); auditSearch.setOnAction(event -> refreshAudit());
        events.getColumns().add(column("Fecha (Nicaragua)", a -> date(a.occurredAt())));
        events.getColumns().add(column("Actor", AuditRow::actorName));
        events.getColumns().add(column("Cuenta", AuditRow::targetName));
        events.getColumns().add(column("Acción", a -> a.action().toString()));
        TableColumn<AuditRow, String> detail = column("Detalle", AuditRow::detail); detail.setPrefWidth(400); events.getColumns().add(detail);
        events.setPlaceholder(new Label("No hay eventos para estos filtros."));
        events.setRowFactory(table -> {
            TableRow<AuditRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && row.getItem() != null) {
                    AuditRow entry = row.getItem();
                    Dialogs.info(this, "Detalle del evento", date(entry.occurredAt()) + "\n" + entry.actorName() + " → " + entry.targetName()
                            + "\n" + entry.action() + "\n" + entry.detail());
                }
            });
            return row;
        });
        Button clear = button("Todas las cuentas", () -> { targetId = null; auditScope.setText("Todas las cuentas"); refreshAudit(); });
        VBox panel = new VBox(10, new FlowPane(7, 6, auditSearch, action, button("Buscar", this::refreshAudit), clear),
                auditScope, events, label("Se muestran los 200 eventos más recientes que coinciden con los filtros.", "muted"));
        panel.getStyleClass().add("panel"); VBox.setVgrow(events, Priority.ALWAYS); return panel;
    }
    private void filter() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        filtered.setPredicate(a -> (role.getValue() == null || a.role() == role.getValue())
                && (!"Activas".equals(state.getValue()) || a.active()) && (!"Inactivas".equals(state.getValue()) || !a.active())
                && (a.username() + " " + a.displayName() + " " + a.email()).toLowerCase(Locale.ROOT).contains(query));
    }
    private void refreshAccounts() {
        run(service::list, values -> { rows.setAll(values); filter(); status.setText(rows.size() + " cuentas registradas."); }, this::error);
    }
    private void refreshAudit() {
        Long target = targetId; AuditAction selected = action.getValue(); String query = auditSearch.getText();
        run(() -> service.audit(target, selected, query), values -> { events.getItems().setAll(values); status.setText(values.size() + " eventos encontrados."); }, this::error);
    }
    private void accountForm(AccountRow selected) {
        TextField username = new TextField(selected == null ? "" : selected.username()); username.setId("account-username"); username.setDisable(selected != null);
        TextField name = new TextField(selected == null ? "" : selected.displayName()); name.setId("account-name");
        TextField email = new TextField(selected == null || selected.email() == null ? "" : selected.email());
        ComboBox<RoleCode> role = new ComboBox<>(FXCollections.observableArrayList(RoleCode.values())); role.setValue(selected == null ? RoleCode.CONSULTA : selected.role()); role.setId("account-role");
        CheckBox active = new CheckBox("Activa"); active.setSelected(selected == null || selected.active());
        if (selected != null && SessionContext.current() != null && selected.username().equals(SessionContext.current().username())) { role.setDisable(true); active.setDisable(true); }
        PasswordField password = new PasswordField(), confirm = new PasswordField(); password.setId("account-password"); confirm.setId("account-confirm");
        Label feedback = label("", "field-error"); feedback.setWrapText(true);
        VBox fields = new VBox(7, new Label("Usuario de acceso (3–40 caracteres)"), username, new Label("Nombre completo"), name,
                new Label("Correo opcional"), email, new Label("Rol"), role, active);
        if (selected == null) fields.getChildren().addAll(new Label("Contraseña temporal (12–128 caracteres)"), password, new Label("Confirmar contraseña"), confirm,
                label("La persona deberá cambiar la contraseña al iniciar sesión.", "muted"));
        fields.getChildren().add(feedback);
        ScrollPane scroll = new ScrollPane(fields); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(selected == null ? 420 : 330);
        Dialogs.form(this, selected == null ? "Nueva cuenta" : "Editar cuenta", scroll, (pane, close) -> {
            if (selected == null && !password.getText().equals(confirm.getText())) { feedback.setText("Las contraseñas no coinciden."); return; }
            String user = username.getText(), fullName = name.getText(), address = email.getText(); RoleCode code = role.getValue(); boolean enabled = active.isSelected();
            char[] secret = password.getText().toCharArray(); password.clear(); confirm.clear(); pane.setDisable(true);
            run(() -> { service.save(selected == null ? null : selected.id(), selected == null ? 0 : selected.revision(), user, fullName, address, code, enabled, secret); return true; },
                    ignored -> { pane.setDisable(false); close.run(); refreshAccounts(); },
                    exception -> { pane.setDisable(false); feedback.setText(ErrorMessages.forUser(exception)); });
        });
    }
    private void resetPassword() {
        AccountRow selected = accounts.getSelectionModel().getSelectedItem(); if (selected == null) return;
        PasswordField password = new PasswordField(), confirm = new PasswordField();
        Label feedback = label("", "field-error"); feedback.setWrapText(true);
        VBox fields = new VBox(9, new Label("Cuenta: " + selected.username()), new Label("Nueva contraseña temporal"), password,
                new Label("Confirmar"), confirm, label("Se cerrarán sus sesiones y deberá cambiarla al iniciar.", "muted"), feedback);
        Dialogs.form(this, "Restablecer contraseña", fields, (pane, close) -> {
            if (!password.getText().equals(confirm.getText())) { feedback.setText("Las contraseñas no coinciden."); return; }
            char[] secret = password.getText().toCharArray(); password.clear(); confirm.clear(); pane.setDisable(true);
            run(() -> { service.resetPassword(selected, secret); return true; }, ignored -> { pane.setDisable(false); close.run(); refreshAccounts(); },
                    exception -> { pane.setDisable(false); feedback.setText(ErrorMessages.forUser(exception)); });
        });
    }
    private void unlock() {
        AccountRow selected = accounts.getSelectionModel().getSelectedItem(); if (selected == null) return;
        Dialogs.confirm(this, "Desbloquear cuenta", "¿Restablecer los intentos fallidos de " + selected.username() + "?", () ->
                run(() -> { service.unlock(selected); return true; }, ignored -> refreshAccounts(), this::error));
    }
    private <T> void run(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        setDisable(true); status.setText("Consultando cuentas…");
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        task.setOnSucceeded(event -> { setDisable(false); success.accept(task.getValue()); });
        task.setOnFailed(event -> { setDisable(false); failure.accept(task.getException()); });
        Thread worker = new Thread(task, "cuentas-hibernate"); worker.setDaemon(true); worker.start();
    }
    private void error(Throwable exception) { status.setText(ErrorMessages.forUser(exception)); Dialogs.error(this, "Cuentas y auditoría", exception); }
    private static String date(Instant value) { return value == null ? "—" : TIME.format(value); }
    private static Label label(String value, String style) { Label label = new Label(value); label.getStyleClass().add(style); return label; }
    private static Button button(String text, Runnable action) { Button button = new Button(text); button.setOnAction(event -> action.run()); return button; }
    private static <T> TableColumn<T, String> column(String title, Function<T, String> text) {
        TableColumn<T, String> column = new TableColumn<>(title); column.setPrefWidth(150);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(text.apply(c.getValue()))); return column;
    }
}
