package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.model.DashboardData.VersionOption;
import com.practice.aulaequilibrio.model.ScheduleReportData.GroupCoverageRow;
import com.practice.aulaequilibrio.report.ReportEngine.Document;
import com.practice.aulaequilibrio.report.ReportEngine.Format;
import com.practice.aulaequilibrio.service.ScheduleReportService;
import com.practice.aulaequilibrio.util.ErrorMessages;
import com.practice.aulaequilibrio.util.ReportActions;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public class GroupCoverageReportController {
    private static final String DESIGN = "/com/practice/aulaequilibrio/reports/cobertura-grupos.jrxml";
    @FXML private ComboBox<VersionOption> version;
    @FXML private CheckBox onlyPending;
    @FXML private Button refreshButton, previewButton, pdfButton, excelButton;
    @FXML private TableView<GroupCoverageRow> groups;
    @FXML private TableColumn<GroupCoverageRow, String> groupColumn, subjectColumn, requiredColumn,
            scheduledColumn, pendingColumn, coverageColumn;
    @FXML private Label status;

    private final ScheduleReportService service;
    private final SimpleBooleanProperty busy = new SimpleBooleanProperty();
    private List<GroupCoverageRow> loaded = List.of();

    public GroupCoverageReportController() { this(new ScheduleReportService()); }
    public GroupCoverageReportController(ScheduleReportService service) { this.service = service; }

    @FXML private void initialize() {
        text(groupColumn, GroupCoverageRow::group);
        text(subjectColumn, GroupCoverageRow::subject);
        text(requiredColumn, row -> String.valueOf(row.required()));
        text(scheduledColumn, row -> String.valueOf(row.scheduled()));
        text(pendingColumn, row -> String.valueOf(row.pending()));
        text(coverageColumn, row -> row.coverage() + "%");
        groups.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        var noReport = busy.or(Bindings.isEmpty(groups.getItems()));
        previewButton.disableProperty().bind(noReport);
        pdfButton.disableProperty().bind(noReport);
        excelButton.disableProperty().bind(noReport);
        refreshButton.disableProperty().bind(busy);
        version.disableProperty().bind(busy);
        onlyPending.disableProperty().bind(busy);
        version.valueProperty().addListener((obs, before, after) -> {
            if (!busy.get()) loadRows();
        });
        onlyPending.selectedProperty().addListener((obs, before, after) -> showRows());
        refresh();
    }

    @FXML private void refresh() {
        var selected = version.getValue();
        run(service::versions, versions -> {
            version.getItems().setAll(versions);
            version.setValue(versions.stream().filter(item -> Objects.equals(item, selected)).findFirst()
                    .orElse(versions.isEmpty() ? null : versions.getFirst()));
            loadRows();
        });
    }

    private void loadRows() {
        var selected = version.getValue();
        run(() -> service.groupCoverage(selected == null ? null : selected.id()), rows -> {
            loaded = rows;
            showRows();
            busy.set(false);
        });
    }

    private void showRows() {
        groups.getItems().setAll(loaded.stream().filter(row -> !onlyPending.isSelected() || row.pending() > 0).toList());
        long pending = groups.getItems().stream().mapToLong(GroupCoverageRow::pending).sum();
        status.setText(groups.getItems().size() + " grupos · " + pending + " sesiones pendientes.");
    }

    @FXML private void preview() { ReportActions.preview(groups, document()); }
    @FXML private void exportPdf() { ReportActions.export(groups, document(), Format.PDF); }
    @FXML private void exportExcel() { ReportActions.export(groups, document(), Format.XLSX); }

    private Document document() {
        String title = "Cobertura por grupo";
        String versionName = version.getValue().name();
        String subtitle = "Versión: " + versionName + (onlyPending.isSelected() ? " · Solo pendientes" : "");
        return new Document(DESIGN, Map.of("TITULO", title, "SUBTITULO", subtitle),
                ScheduleReportService.groupCoverageFields(List.copyOf(groups.getItems())), title + " " + versionName);
    }

    private <T> void run(Callable<T> work, Consumer<T> apply) {
        busy.set(true);
        status.setText("Consultando PostgreSQL…");
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(event -> apply.accept(task.getValue()));
        task.setOnFailed(event -> {
            loaded = List.of();
            groups.getItems().clear();
            busy.set(false);
            status.setText(ErrorMessages.forUser(task.getException()));
        });
        Thread.ofPlatform().daemon().name("reporte-cobertura-grupos").start(task);
    }

    private static void text(TableColumn<GroupCoverageRow, String> column, Function<GroupCoverageRow, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }
}
