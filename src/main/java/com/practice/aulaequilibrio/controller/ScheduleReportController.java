package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.model.DashboardData.VersionOption;
import com.practice.aulaequilibrio.model.ScheduleReportData.Option;
import com.practice.aulaequilibrio.model.ScheduleReportData.Scope;
import com.practice.aulaequilibrio.model.ScheduleReportData.SessionRow;
import com.practice.aulaequilibrio.report.ReportEngine.Format;
import com.practice.aulaequilibrio.service.ScheduleReportService;
import com.practice.aulaequilibrio.util.ErrorMessages;
import com.practice.aulaequilibrio.util.ReportActions;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public class ScheduleReportController {
    private static final Option ALL = new Option(null, "Todos");
    private static final String DESIGN = "/com/practice/aulaequilibrio/reports/horario.jrxml";

    @FXML private ComboBox<VersionOption> version;
    @FXML private ComboBox<Scope> scope;
    @FXML private ComboBox<Option> resource;
    @FXML private Button refreshButton, pdfButton, excelButton;
    @FXML private TableView<SessionRow> sessions;
    @FXML private TableColumn<SessionRow, String> dayColumn, startColumn, endColumn, subjectColumn,
            groupColumn, teacherColumn, classroomColumn;
    @FXML private Label empty, status;

    private final ScheduleReportService service;
    private List<SessionRow> loaded = List.of();
    private boolean applying;

    public ScheduleReportController() { this(new ScheduleReportService()); }
    public ScheduleReportController(ScheduleReportService service) { this.service = service; }

    @FXML private void initialize() {
        text(dayColumn, row -> ScheduleReportService.dayName(row.day()));
        text(startColumn, row -> row.start().toString());
        text(endColumn, row -> row.end().toString());
        text(subjectColumn, SessionRow::subject);
        text(groupColumn, SessionRow::group);
        text(teacherColumn, SessionRow::teacher);
        text(classroomColumn, SessionRow::classroom);
        sessions.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        scope.getItems().setAll(Scope.values());
        scope.getSelectionModel().selectFirst();
        var noRows = Bindings.isEmpty(sessions.getItems());
        pdfButton.disableProperty().bind(noRows);
        excelButton.disableProperty().bind(noRows);
        version.valueProperty().addListener((obs, before, after) -> {
            if (!applying) loadSessions();
        });
        scope.valueProperty().addListener((obs, before, after) -> showOptions());
        resource.valueProperty().addListener((obs, before, after) -> {
            if (!applying) showRows();
        });
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
            loadSessions();
        });
    }

    private void loadSessions() {
        VersionOption selected = version.getValue();
        run(() -> service.sessions(selected == null ? null : selected.id()), rows -> {
            loaded = rows;
            showOptions();
        });
    }

    private void showOptions() {
        Option previous = resource.getValue();
        List<Option> options = new ArrayList<>(List.of(ALL));
        options.addAll(ScheduleReportService.options(loaded, scope.getValue()));
        applying = true;
        resource.getItems().setAll(options);
        resource.setValue(options.contains(previous) ? previous : ALL);
        applying = false;
        showRows();
    }

    private void showRows() {
        Option selected = resource.getValue() == ALL ? null : resource.getValue();
        sessions.getItems().setAll(ScheduleReportService.filter(loaded, scope.getValue(), selected));
        empty.setText(version.getValue() == null ? "No hay versiones de horario registradas."
                : "La versión no tiene sesiones para esta selección.");
        status.setText(sessions.getItems().size() + " sesiones en el reporte.");
    }

    @FXML private void exportPdf() {
        export(Format.PDF);
    }

    @FXML private void exportExcel() {
        export(Format.XLSX);
    }

    private void export(Format format) {
        String versionName = version.getValue() == null ? "" : version.getValue().name();
        String subtitle = "Versión: " + versionName + (resource.getValue() == ALL ? ""
                : " · " + scope.getValue() + ": " + resource.getValue().label());
        ReportActions.export(sessions, DESIGN, Map.of("TITULO", "Horario semanal", "SUBTITULO", subtitle),
                ScheduleReportService.scheduleFields(List.copyOf(sessions.getItems())), format, "horario " + versionName);
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
        Thread worker = new Thread(task, "reporte-horario");
        worker.setDaemon(true);
        worker.start();
    }

    private static void text(TableColumn<SessionRow, String> column, Function<SessionRow, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }
}
