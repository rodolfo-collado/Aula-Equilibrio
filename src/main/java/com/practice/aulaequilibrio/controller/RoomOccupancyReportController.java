package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.model.DashboardData.VersionOption;
import com.practice.aulaequilibrio.model.ScheduleReportData.Option;
import com.practice.aulaequilibrio.model.ScheduleReportData.RoomOccupancyRow;
import com.practice.aulaequilibrio.report.ReportEngine.Format;
import com.practice.aulaequilibrio.service.ScheduleReportService;
import com.practice.aulaequilibrio.util.ErrorMessages;
import com.practice.aulaequilibrio.util.ReportActions;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public class RoomOccupancyReportController {
    private static final String DESIGN = "/com/practice/aulaequilibrio/reports/ocupacion-aulas.jrxml";
    private static final Option WEEK = new Option(null, "Semana completa");

    @FXML private ComboBox<VersionOption> version;
    @FXML private ComboBox<Option> day;
    @FXML private Button refreshButton, pdfButton, excelButton;
    @FXML private TableView<RoomOccupancyRow> rooms;
    @FXML private TableColumn<RoomOccupancyRow, String> roomColumn, typeColumn, capacityColumn, stateColumn,
            sessionsColumn, hoursColumn, usageColumn;
    @FXML private Label empty, status;

    private final ScheduleReportService service;
    private final SimpleBooleanProperty busy = new SimpleBooleanProperty();

    public RoomOccupancyReportController() { this(new ScheduleReportService()); }
    public RoomOccupancyReportController(ScheduleReportService service) { this.service = service; }

    @FXML private void initialize() {
        day.getItems().add(WEEK);
        for (DayOfWeek weekday : DayOfWeek.values()) {
            day.getItems().add(new Option(weekday.getValue(), ScheduleReportService.dayName(weekday.getValue())));
        }
        day.setValue(WEEK);
        text(roomColumn, RoomOccupancyRow::classroom);
        text(typeColumn, RoomOccupancyRow::type);
        text(capacityColumn, row -> String.valueOf(row.capacity()));
        text(stateColumn, row -> row.state().toString());
        text(sessionsColumn, row -> String.valueOf(row.sessions()));
        text(hoursColumn, row -> String.format("%.2f", row.occupiedMinutes() / 60.0));
        text(usageColumn, row -> {
            Double usage = row.usage(ScheduleReportService.roomPeriodMinutes(day.getValue().id()));
            return usage == null ? "—" : String.format(Locale.ROOT, "%.1f%%", usage * 100);
        });
        rooms.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        var cannotExport = busy.or(Bindings.isEmpty(rooms.getItems()));
        pdfButton.disableProperty().bind(cannotExport);
        excelButton.disableProperty().bind(cannotExport);
        refreshButton.disableProperty().bind(busy);
        version.disableProperty().bind(busy);
        day.disableProperty().bind(busy);
        version.valueProperty().addListener((obs, before, after) -> {
            if (!busy.get()) loadRows();
        });
        day.valueProperty().addListener((obs, before, after) -> loadRows());
        refresh();
    }

    @FXML private void refresh() {
        Integer current = version.getValue() == null ? null : version.getValue().id();
        run(service::versions, versions -> {
            version.getItems().setAll(versions);
            version.setValue(versions.stream().filter(v -> Objects.equals(v.id(), current)).findFirst()
                    .orElse(versions.isEmpty() ? null : versions.getFirst()));
            loadRows();
        });
    }

    private void loadRows() {
        VersionOption selected = version.getValue();
        Integer selectedDay = day.getValue().id();
        rooms.getItems().clear();
        run(() -> service.roomOccupancy(selected == null ? null : selected.id(), selectedDay), rows -> {
            rooms.getItems().setAll(rows);
            empty.setText(selected == null ? "No hay versiones de horario registradas." : "No hay aulas registradas.");
            status.setText(rows.size() + " aulas · " + rows.stream().mapToLong(RoomOccupancyRow::sessions).sum()
                    + " sesiones en el periodo.");
            busy.set(false);
        });
    }

    @FXML private void exportPdf() { export(Format.PDF); }
    @FXML private void exportExcel() { export(Format.XLSX); }

    private void export(Format format) {
        var selected = version.getValue();
        var period = day.getValue();
        if (selected == null || busy.get() || rooms.getItems().isEmpty()) return;
        String subtitle = "Versión: " + selected.name() + " · " + period.label();
        ReportActions.export(rooms, DESIGN, Map.of("TITULO", "Ocupación de aulas", "SUBTITULO", subtitle),
                ScheduleReportService.roomOccupancyFields(List.copyOf(rooms.getItems()), period.id()), format,
                "ocupación de aulas " + selected.name() + " " + period.label());
    }

    private <T> void run(Callable<T> work, Consumer<T> apply) {
        busy.set(true);
        status.setText("Consultando PostgreSQL…");
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(event -> apply.accept(task.getValue()));
        task.setOnFailed(event -> {
            rooms.getItems().clear();
            busy.set(false);
            status.setText(ErrorMessages.forUser(task.getException()));
        });
        Thread worker = new Thread(task, "reporte-ocupacion-aulas");
        worker.setDaemon(true);
        worker.start();
    }

    private static void text(TableColumn<RoomOccupancyRow, String> column,
                             Function<RoomOccupancyRow, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }
}
