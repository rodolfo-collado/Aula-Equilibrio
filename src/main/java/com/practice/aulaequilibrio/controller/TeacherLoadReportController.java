package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.model.DashboardData.VersionOption;
import com.practice.aulaequilibrio.model.ScheduleReportData.TeacherLoadRow;
import com.practice.aulaequilibrio.report.ReportEngine.Format;
import com.practice.aulaequilibrio.service.ScheduleReportService;
import com.practice.aulaequilibrio.util.ErrorMessages;
import com.practice.aulaequilibrio.util.ReportActions;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public class TeacherLoadReportController {
    private static final String DESIGN = "/com/practice/aulaequilibrio/reports/carga-docente.jrxml";

    @FXML private ComboBox<VersionOption> version;
    @FXML private CheckBox onlyActive;
    @FXML private Button refreshButton, pdfButton, excelButton;
    @FXML private TableView<TeacherLoadRow> teachers;
    @FXML private TableColumn<TeacherLoadRow, String> nameColumn, stateColumn, sessionsColumn,
            assignedColumn, availableColumn, usageColumn;
    @FXML private Label empty, status;

    private final ScheduleReportService service;
    private List<TeacherLoadRow> loaded = List.of();
    private boolean applying;

    public TeacherLoadReportController() { this(new ScheduleReportService()); }
    public TeacherLoadReportController(ScheduleReportService service) { this.service = service; }

    @FXML private void initialize() {
        text(nameColumn, TeacherLoadRow::teacher);
        text(stateColumn, row -> row.active() ? "Activo" : "Inactivo");
        text(sessionsColumn, row -> String.valueOf(row.sessions()));
        text(assignedColumn, row -> hours(row.assignedMinutes()));
        text(availableColumn, row -> hours(row.availableMinutes()));
        text(usageColumn, row -> row.usage() == null ? "—"
                : String.format(Locale.ROOT, "%.0f%%", row.usage() * 100));
        teachers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        var noRows = Bindings.isEmpty(teachers.getItems());
        pdfButton.disableProperty().bind(noRows);
        excelButton.disableProperty().bind(noRows);
        version.valueProperty().addListener((obs, before, after) -> {
            if (!applying) loadRows();
        });
        onlyActive.selectedProperty().addListener((obs, before, after) -> showRows());
        refresh();
    }

    @FXML private void refresh() {
        Integer selected = version.getValue() == null ? null : version.getValue().id();
        run(service::versions, versions -> {
            applying = true;
            version.getItems().setAll(versions);
            version.setValue(versions.stream().filter(v -> Objects.equals(v.id(), selected)).findFirst()
                    .orElse(versions.isEmpty() ? null : versions.getFirst()));
            applying = false;
            loadRows();
        });
    }

    private void loadRows() {
        VersionOption selected = version.getValue();
        run(() -> service.teacherLoad(selected == null ? null : selected.id()), rows -> {
            loaded = rows;
            showRows();
        });
    }

    private void showRows() {
        boolean active = onlyActive.isSelected();
        teachers.getItems().setAll(loaded.stream().filter(row -> !active || row.active()).toList());
        empty.setText(version.getValue() == null ? "No hay versiones de horario registradas."
                : "No hay docentes para esta selección.");
        long idle = teachers.getItems().stream().filter(row -> row.active() && row.sessions() == 0).count();
        status.setText(teachers.getItems().size() + " docentes · " + idle + " activos sin sesiones asignadas.");
    }

    @FXML private void exportPdf() {
        export(Format.PDF);
    }

    @FXML private void exportExcel() {
        export(Format.XLSX);
    }

    private void export(Format format) {
        String versionName = version.getValue() == null ? "" : version.getValue().name();
        String subtitle = "Versión: " + versionName + (onlyActive.isSelected() ? " · Solo docentes activos" : "");
        ReportActions.export(teachers, DESIGN, Map.of("TITULO", "Carga docente", "SUBTITULO", subtitle),
                ScheduleReportService.teacherLoadFields(List.copyOf(teachers.getItems())), format,
                "carga docente " + versionName);
    }

    static String hours(long minutes) {
        return String.format("%.1f", minutes / 60.0);
    }

    private <T> void run(Callable<T> work, Consumer<T> apply) {
        refreshButton.setDisable(true);
        status.setText("Consultando PostgreSQL…");
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(event -> {
            refreshButton.setDisable(false);
            apply.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            refreshButton.setDisable(false);
            status.setText(ErrorMessages.forUser(task.getException()));
        });
        Thread worker = new Thread(task, "reporte-carga-docente");
        worker.setDaemon(true);
        worker.start();
    }

    private static void text(TableColumn<TeacherLoadRow, String> column, Function<TeacherLoadRow, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }
}
