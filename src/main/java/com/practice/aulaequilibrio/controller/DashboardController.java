package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.model.DashboardData.*;
import com.practice.aulaequilibrio.service.DashboardService;
import com.practice.aulaequilibrio.util.ErrorMessages;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.layout.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;

public class DashboardController {
    @FXML private ScrollPane dashboard;
    @FXML private TilePane metrics;
    @FXML private SplitPane detailPanels;
    @FXML private ComboBox<VersionOption> version;
    @FXML private Button refreshButton;
    @FXML private Label facultyCount, facultyDetail, subjectCount, groupCount, teacherCount, teacherDetail;
    @FXML private Label roomCount, roomDetail, coverageCount, coverageDetail, summary, updated, error;
    @FXML private ProgressBar coverage;
    @FXML private TableView<FacultyRow> faculties;
    @FXML private TableColumn<FacultyRow, String> facultyName;
    @FXML private TableColumn<FacultyRow, Long> careers, subjects, groups, classrooms, assignments;
    @FXML private VBox ranking, notices;
    @FXML private TilePane piePanels;
    @FXML private PieChart roomPie, coveragePie;
    @FXML private BarChart<String, Number> weekChart;
    @FXML private Label roomChartEmpty, coverageChartEmpty, weekChartEmpty;

    private final DashboardService service;
    private Consumer<Destination> navigator = destination -> {};
    private boolean loading;
    private boolean applying;
    private VersionOption displayedVersion;

    public DashboardController() { this(new DashboardService()); }
    public DashboardController(DashboardService service) { this.service = service; }

    public void setNavigator(Consumer<Destination> navigator) { this.navigator = navigator; }

    @FXML private void initialize() {
        facultyName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name()));
        careers.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().careers()));
        subjects.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().subjects()));
        groups.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().groups()));
        classrooms.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().classrooms()));
        assignments.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().assignments()));
        faculties.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        version.valueProperty().addListener((obs, before, after) -> {
            if (!applying) refresh();
        });
        error.managedProperty().bind(error.visibleProperty());
        error.setVisible(false);
        dashboard.viewportBoundsProperty().addListener((obs, before, bounds) -> {
            double width = Math.max(1, bounds.getWidth() - 44);
            int columns = width >= 1180 ? 6 : width >= 800 ? 3 : width >= 500 ? 2 : 1;
            metrics.setPrefTileWidth(Math.max(1, (width - 12 * (columns - 1)) / columns));
            piePanels.setPrefTileWidth(Math.max(1, (width - (width >= 650 ? 12 : 0)) / (width >= 650 ? 2 : 1)));
            boolean narrow = width < 800;
            detailPanels.setOrientation(narrow ? Orientation.VERTICAL : Orientation.HORIZONTAL);
            detailPanels.setPrefHeight(narrow ? 740 : 400);
        });
    }

    @FXML public void refresh() {
        if (loading) return;
        loading = true;
        refreshButton.setDisable(true);
        version.setDisable(true);
        updated.setText("Actualizando desde PostgreSQL…");
        error.setVisible(false);
        Integer selected = version.getValue() == null ? null : version.getValue().id();
        Task<Snapshot> task = new Task<>() {
            @Override protected Snapshot call() { return service.load(selected); }
        };
        task.setOnSucceeded(event -> {
            render(task.getValue());
            finish();
            updated.setText("Actualizado a las " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        });
        task.setOnFailed(event -> {
            applying = true;
            version.setValue(displayedVersion);
            applying = false;
            finish();
            updated.setText("No se pudo actualizar. Los datos visibles pueden estar desactualizados.");
            error.setText(ErrorMessages.forUser(task.getException()) + " Pulsa Actualizar para reintentar.");
            error.setVisible(true);
        });
        Thread worker = new Thread(task, "dashboard-hibernate");
        worker.setDaemon(true);
        worker.start();
    }

    private void finish() {
        loading = false;
        refreshButton.setDisable(false);
        version.setDisable(version.getItems().isEmpty());
    }

    private void render(Snapshot data) {
        applying = true;
        version.getItems().setAll(data.versions());
        version.setValue(data.selectedVersion());
        displayedVersion = data.selectedVersion();
        applying = false;
        facultyCount.setText(number(data.faculties()));
        facultyDetail.setText(number(data.careers()) + " carreras registradas");
        subjectCount.setText(number(data.subjects()));
        groupCount.setText(number(data.groups()));
        teacherCount.setText(number(data.activeTeachers()));
        teacherDetail.setText("de " + number(data.teachers()) + " docentes registrados");
        roomCount.setText(number(data.availableRooms()) + " / " + number(data.rooms()));
        roomDetail.setText(number(data.availableSeats()) + " plazas en aulas disponibles");
        boolean hasVersion = data.selectedVersion() != null;
        coverageCount.setText(!hasVersion || data.requiredSessions() == 0 ? "—"
                : Math.round(data.coverage() * 100) + "%");
        coverage.setProgress(data.coverage());
        coverageDetail.setText(!hasVersion ? "Crea una versión de horario"
                : data.requiredSessions() == 0 ? "Sin sesiones requeridas por grupos"
                : data.coveredSessions() + " de " + data.requiredSessions() + " sesiones semanales");
        summary.setText(!hasVersion ? "Sin versión: los indicadores de uso todavía no están disponibles."
                : data.assignments() + " asignaciones  ·  " + data.scheduledGroups() + " de " + data.groups()
                + " grupos con horario  ·  " + data.usedRooms() + (data.usedRooms() == 1 ? " aula utilizada" : " aulas utilizadas"));
        faculties.getItems().setAll(data.facultyRows());
        faculties.sort();
        ranking.getChildren().clear();
        var leaders = data.facultyRows().stream().filter(f -> f.classrooms() > 0)
                .sorted(Comparator.comparingLong(FacultyRow::classrooms).reversed().thenComparing(FacultyRow::name))
                .limit(5).toList();
        if (leaders.isEmpty()) ranking.getChildren().add(wrapped("Todavía no hay aulas asociadas a facultades en esta versión.", "muted"));
        for (FacultyRow faculty : leaders) {
            Label title = wrapped(faculty.name(), "card-title");
            ProgressBar bar = new ProgressBar((double) faculty.classrooms() / leaders.getFirst().classrooms());
            bar.setMaxWidth(Double.MAX_VALUE);
            VBox row = new VBox(5, title, bar, wrapped(faculty.classrooms()
                    + (faculty.classrooms() == 1 ? " aula distinta · " : " aulas distintas · ")
                    + faculty.assignments() + (faculty.assignments() == 1 ? " sesión asignada" : " sesiones asignadas"), "muted"));
            row.getStyleClass().add("dashboard-ranking-row");
            ranking.getChildren().add(row);
        }
        notices.getChildren().clear();
        renderCharts(data);
        if (data.notices().isEmpty()) notices.getChildren().add(wrapped("Sin pendientes en estos indicadores.", "muted"));
        for (Notice notice : data.notices()) {
            Button action = new Button("Revisar →");
            action.getStyleClass().add("dashboard-review");
            action.setOnAction(event -> navigator.accept(notice.destination()));
            VBox row = new VBox(4, wrapped(notice.title(), "card-title"), wrapped(notice.detail(), "muted"), action);
            row.getStyleClass().add("dashboard-notice");
            notices.getChildren().add(row);
        }
    }

    private void renderCharts(Snapshot data) {
        pie(roomPie, roomChartEmpty, data.roomStates(), Destination.ROOMS);
        pie(coveragePie, coverageChartEmpty, data.selectedVersion() == null ? java.util.List.of() : java.util.List.of(
                new ChartValue("covered", "Cubiertas", data.coveredSessions()),
                new ChartValue("pending", "Pendientes", data.requiredSessions() - data.coveredSessions())), Destination.SCHEDULES);
        weekChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        data.sessionsByDay().forEach(value -> {
            XYChart.Data<String, Number> point = new XYChart.Data<>(value.label(), value.value());
            point.nodeProperty().addListener((obs, before, node) -> {
                if (node != null) {
                    Tooltip.install(node, new Tooltip(value.label() + ": " + value.value() + " sesiones. Pulsa para abrir Horarios."));
                    node.setOnMouseClicked(event -> navigator.accept(Destination.SCHEDULES));
                }
            });
            series.getData().add(point);
        });
        weekChart.getData().add(series);
        long maximum = data.sessionsByDay().stream().mapToLong(ChartValue::value).max().orElse(0);
        double step = Math.max(1, Math.ceil(maximum / 5.0));
        NumberAxis axis = (NumberAxis) weekChart.getYAxis();
        axis.setAutoRanging(false);
        axis.setLowerBound(0);
        axis.setUpperBound(Math.max(step, Math.ceil((maximum + 1) / step) * step));
        axis.setTickUnit(step);
        weekChartEmpty.setVisible(data.assignments() == 0);
    }

    private void pie(PieChart chart, Label empty, java.util.List<ChartValue> values, Destination target) {
        chart.getData().clear();
        long total = values.stream().mapToLong(ChartValue::value).sum();
        empty.setVisible(total == 0);
        if (total == 0) return;
        for (ChartValue value : values) {
            PieChart.Data slice = new PieChart.Data(value.label() + " · " + value.value(), value.value());
            slice.nodeProperty().addListener((obs, before, node) -> {
                if (node != null) {
                    node.getStyleClass().add("slice-" + value.key());
                    Tooltip.install(node, new Tooltip(value.label() + ": " + value.value() + " ("
                            + Math.round(100.0 * value.value() / total) + "%). Pulsa para revisar."));
                    node.setOnMouseClicked(event -> navigator.accept(target));
                }
            });
            chart.getData().add(slice);
        }
    }

    @FXML private void navigate(ActionEvent event) {
        navigator.accept(Destination.valueOf(((Button) event.getSource()).getUserData().toString()));
    }
    private static Label wrapped(String text, String style) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(style);
        label.setMinWidth(0);
        return label;
    }
    private static String number(long value) { return String.format(Locale.forLanguageTag("es-NI"), "%,d", value); }
}
