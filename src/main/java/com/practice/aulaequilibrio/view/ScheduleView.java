package com.practice.aulaequilibrio.view;

import com.practice.aulaequilibrio.model.ScheduleAssignment;
import com.practice.aulaequilibrio.model.ScheduleVersion;
import com.practice.aulaequilibrio.model.ScheduleData.*;
import com.practice.aulaequilibrio.service.ScheduleService;
import com.practice.aulaequilibrio.model.Permission;
import com.practice.aulaequilibrio.util.UiAccess;
import com.practice.aulaequilibrio.util.Dialogs;
import com.practice.aulaequilibrio.util.Validations;
import com.practice.aulaequilibrio.util.ValidationException;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.StringConverter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Duration;
import com.practice.aulaequilibrio.util.TimeIntervals;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public class ScheduleView extends BorderPane {
    private final ScheduleService service;
    private final boolean editable;
    private final SimpleBooleanProperty busy = new SimpleBooleanProperty();
    private final ComboBox<ScheduleVersion> versions = new ComboBox<>();
    private final ComboBox<String> filterType = new ComboBox<>();
    private final ComboBox<Option> resource = new ComboBox<>();
    private final TextField search = new TextField();
    private final CheckBox weekends = new CheckBox("Sáb / dom");
    private final GridPane week = new GridPane();
    private final ScrollPane weekScroll = new ScrollPane(week);
    private final BorderPane viewer = new BorderPane();
    private final TableView<ScheduleAssignment> table = new TableView<>();
    private final ListView<GroupOption> pending = new ListView<>();
    private final ComboBox<GroupOption> group = new ComboBox<>();
    private final ComboBox<Option> subject = new ComboBox<>(), teacher = new ComboBox<>();
    private final ComboBox<RoomOption> classroom = new ComboBox<>();
    private final ComboBox<DayOfWeek> day = new ComboBox<>();
    private final TextField startTime = new TextField("08:00"), endTime = new TextField("09:00");
    private final Label editorTitle = new Label("Nueva sesión"), groupProgress = new Label(), feedback = new Label();
    private final Label message = new Label(), summary = new Label();
    private final Button save = new Button("Agregar sesión"), remove = new Button("Eliminar sesión");
    private Catalogs catalogs = new Catalogs(List.of(), List.of(), List.of(), List.of(), List.of());
    private List<ScheduleAssignment> assignments = List.of();
    private Integer editingId;
    private Integer loadedVersionId;
    private boolean applying;
    private record Loaded(Catalogs catalogs, List<ScheduleVersion> versions, Integer selected, List<ScheduleAssignment> assignments) {}

    public ScheduleView() { this(new ScheduleService()); }
    public ScheduleView(ScheduleService service) {
        this.service = service;
        this.editable = UiAccess.can(Permission.EDIT_SCHEDULES);
        getStylesheets().add(getClass().getResource("/com/practice/aulaequilibrio/styles/aula.css").toExternalForm());
        getStyleClass().addAll("schedule-page", "module-page");
        disableProperty().bind(busy); setId("schedule-planner");
        buildHeader(); buildViewer();
        ScrollPane editorScroll = new ScrollPane(buildEditor());
        editorScroll.setFitToWidth(true); editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        editorScroll.getStyleClass().add("dashboard-scroll");
        BorderPane editorPane = new BorderPane(editorScroll);
        editorPane.setMinWidth(310);
        FlowPane actions = new FlowPane(7, 6, save, remove); actions.getStyleClass().add("schedule-actions");
        editorPane.setBottom(actions);
        SplitPane workspace = editable ? new SplitPane(viewer, editorPane) : new SplitPane(viewer);
        workspace.setDividerPositions(0.69);
        workspace.getStyleClass().add("module-split"); setCenter(workspace);
        message.setId("schedule-message"); message.setWrapText(true); message.getStyleClass().add("muted"); setBottom(message);
        versions.valueProperty().addListener((obs, before, after) -> { if (!applying) reload(selectedVersionId()); });
        filterType.valueProperty().addListener((obs, before, after) -> { if (!applying) { fillResources(null); render(); } });
        resource.valueProperty().addListener((obs, before, after) -> { if (!applying) render(); });
        search.textProperty().addListener((obs, before, after) -> { if (!applying) render(); });
        weekends.selectedProperty().addListener((obs, before, after) -> { if (!applying) render(); });
        group.valueProperty().addListener((obs, before, after) -> { if (!applying) { applyGroupRules(true); updateRooms(); } });
        for (ComboBox<?> chooser : List.of(teacher, day)) chooser.valueProperty().addListener((obs, before, after) -> { if (!applying) updateRooms(); });
        subject.valueProperty().addListener((obs, before, after) -> { if (!applying) { proposeEnd(); updateRooms(); } });
        classroom.valueProperty().addListener((obs, before, after) -> { if (!applying) preview(); });
        startTime.textProperty().addListener((obs, before, after) -> { if (!applying) { proposeEnd(); updateRooms(); } });
        endTime.textProperty().addListener((obs, before, after) -> { if (!applying) updateRooms(); });
        table.getSelectionModel().selectedItemProperty().addListener((obs, before, after) -> { if (!applying && after != null && editable) edit(after); });
        save.setOnAction(event -> saveSession()); remove.setOnAction(event -> deleteSession());
        day.setValue(DayOfWeek.MONDAY); preview();
    }

    private void buildHeader() {
        versions.setId("schedule-versions"); versions.setPrefWidth(255); versions.setPromptText("Crea una versión para comenzar");
        versions.setConverter(display(v -> v.getName() + " · " + v.getCreatedOn()));
        Button rename = button("Renombrar", () -> versionDialog(false, versions.getValue()));
        Button copy = button("Duplicar", () -> versionDialog(true, versions.getValue()));
        Button delete = button("Eliminar versión", this::deleteVersion); delete.getStyleClass().add("danger");
        for (Button button : List.of(rename, copy, delete)) button.disableProperty().bind(versions.valueProperty().isNull());
        FlowPane versionBar = new FlowPane(8, 6, versions, button("Nueva versión", () -> versionDialog(false, null)), rename, copy, delete,
                button("Actualizar", () -> reload(selectedVersionId())));
        filterType.getItems().addAll("Grupo", "Docente", "Aula"); filterType.setValue("Grupo"); filterType.setId("schedule-filter-type");
        resource.setId("schedule-filter"); resource.setPrefWidth(210);
        search.setId("schedule-search"); search.setPromptText("Buscar clase, grupo, docente o aula"); search.setPrefWidth(250);
        weekends.setTooltip(new Tooltip("Amplía la cuadrícula al fin de semana. Las sesiones de sábado o domingo existentes siempre se muestran."));
        FlowPane filters = new FlowPane(8, 6, filterType, resource, search, weekends);
        summary.setWrapText(true); summary.getStyleClass().add("muted");
        VBox header = new VBox(9, label("Planificador semanal", "page-title"), versionBar, filters, summary);
        if (!editable) versionBar.getChildren().removeIf(node -> node != versions && node instanceof Button button && !button.getText().equals("Actualizar"));
        header.getStyleClass().add("panel"); setTop(header);
    }

    private void buildViewer() {
        ToggleGroup mode = new ToggleGroup(); ToggleButton weekly = new ToggleButton("Semana"), list = new ToggleButton("Lista");
        weekly.setToggleGroup(mode); list.setToggleGroup(mode); weekly.setSelected(true);
        weekly.setOnAction(event -> { weekly.setSelected(true); viewer.setCenter(weekScroll); });
        list.setOnAction(event -> { list.setSelected(true); viewer.setCenter(table); });
        viewer.setTop(new VBox(8, new FlowPane(8, 6, weekly, list), wrapped(editable
                ? "Universidad abierta de 07:00 a 21:00. Pulsa una hora para agregar o arrastra una clase para moverla."
                : "Consulta de horarios · 07:00 a 21:00. Filtra la semana y pulsa una clase para ver sus detalles.", "muted")));
        viewer.getStyleClass().add("schedule-viewer"); week.setId("schedule-week"); week.setHgap(5); week.setVgap(5);
        week.getColumnConstraints().add(new ColumnConstraints(65));
        for (int i = 0; i < 7; i++) { ColumnConstraints col = new ColumnConstraints(); col.setMinWidth(120); col.setHgrow(Priority.ALWAYS); week.getColumnConstraints().add(col); }
        weekScroll.setFitToWidth(true); weekScroll.getStyleClass().add("dashboard-scroll"); viewer.setCenter(weekScroll);
        table.setId("schedule-table");
        table.getColumns().add(column("Día", a -> dayLabel(DayOfWeek.of(a.getDay()))));
        table.getColumns().add(column("Horario", a -> a.hasTime() ? format(a.getStartTime()) + " – " + format(a.getEndTime()) : "Pendiente de definir"));
        table.getColumns().add(column("Grupo", a -> a.getGroup().getNombre()));
        table.getColumns().add(column("Asignatura", a -> a.getSubject().getNombre()));
        table.getColumns().add(column("Docente", a -> a.getTeacher().getName()));
        table.getColumns().add(column("Aula", a -> a.getClassroom().getCodigo()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); table.setPlaceholder(new Label("No hay sesiones para estos filtros."));
    }

    private VBox buildEditor() {
        editorTitle.getStyleClass().add("card-title");
        group.setId("session-group"); subject.setId("session-subject"); teacher.setId("session-teacher"); classroom.setId("session-room");
        day.setId("session-day"); startTime.setId("session-start"); endTime.setId("session-end"); save.setId("session-save"); remove.setId("session-delete"); feedback.setId("session-feedback");
        group.setPromptText("Selecciona un grupo"); teacher.setPromptText("Selecciona un docente activo"); classroom.setPromptText("Aulas compatibles y libres");
        for (ComboBox<?> chooser : List.of(group, subject, teacher, classroom, day)) chooser.setMaxWidth(Double.MAX_VALUE);
        day.getItems().setAll(DayOfWeek.values()); day.setConverter(display(ScheduleView::dayLabel));
        groupProgress.setWrapText(true); groupProgress.getStyleClass().add("muted"); feedback.setWrapText(true); feedback.getStyleClass().add("field-error");
        save.getStyleClass().add("primary"); remove.getStyleClass().add("danger");
        pending.setId("schedule-pending"); pending.setPrefHeight(175); pending.setPlaceholder(new Label("Sin grupos registrados."));
        pending.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(GroupOption item, boolean empty) {
                super.updateItem(item, empty); setText(empty || item == null ? null : item.name() + " · " + countSessions(item.id()) + " / "
                        + (item.sessions() == null ? "?" : item.sessions()) + " sesiones");
            }
        });
        pending.setOnMouseClicked(event -> { if (pending.getSelectionModel().getSelectedItem() != null) {
            newSession(day.getValue(), readTime(startTime)); group.setValue(pending.getSelectionModel().getSelectedItem());
        }});
        GridPane fields = new GridPane(); fields.setHgap(8); fields.setVgap(8);
        ColumnConstraints caption = new ColumnConstraints(80), input = new ColumnConstraints(); input.setHgrow(Priority.ALWAYS);
        fields.getColumnConstraints().addAll(caption, input);
        fields.addRow(0, new Label("Grupo *"), group); fields.addRow(1, new Label("Asignatura *"), subject);
        fields.addRow(2, new Label("Docente *"), teacher); fields.addRow(3, new Label("Día *"), day);
        fields.addRow(4, new Label("Inicio *"), startTime); fields.addRow(5, new Label("Fin *"), endTime); fields.addRow(6, new Label("Aula *"), classroom);
        VBox editor = new VBox(8, editorTitle, button("Nueva sesión", () -> newSession(day.getValue(), readTime(startTime))),
                fields, groupProgress, feedback, new Separator(),
                wrapped("Grupos · sesiones programadas / requeridas", "card-title"), pending,
                wrapped("Horario de la universidad: 07:00–21:00. Usa HH:mm; respeta la duración y la disponibilidad docente.", "muted"));
        editor.getStyleClass().add("panel"); return editor;
    }

    public void load() { reload(null); }
    private void reload(Integer preferred) {
        Integer keepGroup = group.getValue() == null ? null : group.getValue().id(), keepResource = resource.getValue() == null ? null : resource.getValue().id();
        execute(() -> {
            Catalogs options = service.listCatalogs(); var available = service.listVersions();
            Integer selected = available.stream().filter(v -> v.getId().equals(preferred)).map(ScheduleVersion::getId).findFirst()
                    .orElse(available.isEmpty() ? null : available.getFirst().getId());
            return new Loaded(options, available, selected, selected == null ? List.of() : service.listAssignments(selected, null));
        }, loaded -> {
            applying = true; loadedVersionId = loaded.selected(); catalogs = loaded.catalogs(); assignments = loaded.assignments(); versions.getItems().setAll(loaded.versions());
            versions.setValue(loaded.versions().stream().filter(v -> v.getId().equals(loaded.selected())).findFirst().orElse(null));
            group.getItems().setAll(catalogs.groups()); subject.getItems().setAll(catalogs.subjects()); teacher.getItems().setAll(catalogs.teachers());
            group.setValue(catalogs.groups().stream().filter(g -> g.id().equals(keepGroup)).findFirst().orElse(null)); fillResources(keepResource);
            boolean hasWeekends = assignments.stream().anyMatch(a -> a.getDay() > 5);
            if (hasWeekends) weekends.setSelected(true);
            weekends.setDisable(hasWeekends);
            pending.getItems().setAll(catalogs.groups().stream().sorted(Comparator.comparingLong((GroupOption g) ->
                    Math.max(0, (g.sessions() == null ? 0 : g.sessions()) - countSessions(g.id()))).reversed().thenComparing(GroupOption::name)).toList());
            applying = false; newSession(day.getValue(), readTime(startTime));
            message.setText(loaded.selected() == null ? "Crea una versión y selecciona un grupo para comenzar."
                    : "Datos actualizados. Los cruces de grupo, docente y aula se vuelven a validar al guardar.");
        }, exception -> {
            applying = true;
            versions.setValue(versions.getItems().stream().filter(v -> v.getId().equals(loadedVersionId)).findFirst().orElse(null));
            applying = false; showError(exception);
        });
    }

    private void fillResources(Integer selected) {
        boolean wasApplying = applying; applying = true;
        List<Option> values = switch (filterType.getValue()) {
            case "Docente" -> assignments.stream().map(a -> new Option(a.getTeacher().getId(), a.getTeacher().getName())).distinct().sorted(Comparator.comparing(Option::label)).toList();
            case "Aula" -> assignments.stream().map(a -> new Option(a.getClassroom().getId(), a.getClassroom().getCodigo())).distinct().sorted(Comparator.comparing(Option::label)).toList();
            default -> catalogs.groups().stream().map(g -> new Option(g.id(), g.name())).toList();
        };
        resource.getItems().setAll(new Option(null, "Todos")); resource.getItems().addAll(values);
        resource.setValue(resource.getItems().stream().filter(o -> Objects.equals(o.id(), selected)).findFirst().orElse(resource.getItems().getFirst())); applying = wasApplying;
    }

    private List<ScheduleAssignment> visible() {
        Integer id = resource.getValue() == null ? null : resource.getValue().id(); String query = search.getText().strip().toLowerCase(Locale.ROOT);
        return assignments.stream().filter(a -> id == null || id.equals(switch (filterType.getValue()) {
            case "Docente" -> a.getTeacher().getId(); case "Aula" -> a.getClassroom().getId(); default -> a.getGroup().getId();
        })).filter(a -> (a.getGroup().getNombre() + " " + a.getSubject().getNombre() + " " + a.getTeacher().getName() + " "
                + a.getClassroom().getCodigo()).toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private static final double MINUTE_HEIGHT = 1.8;

    private void render() {
        var shown = visible();
        applying = true; table.getItems().setAll(shown); applying = false;
        week.getChildren().clear(); week.getColumnConstraints().clear();
        int days = weekends.isSelected() || assignments.stream().anyMatch(a -> a.getDay() > 5) ? 7 : 5;
        int firstMinute = 7 * 60;
        int lastMinute = 21 * 60;
        double height = (lastMinute - firstMinute) * MINUTE_HEIGHT;
        week.getColumnConstraints().add(new ColumnConstraints(65));
        week.add(label("Hora", "schedule-day-heading"), 0, 0);
        Pane times = new Pane(); times.setPrefHeight(height);
        for (int minute = firstMinute; minute < lastMinute; minute += 30) {
            Label time = label(format(LocalTime.of(minute / 60, minute % 60)), "schedule-time-heading");
            time.relocate(3, (minute - firstMinute) * MINUTE_HEIGHT); times.getChildren().add(time);
        }
        week.add(times, 0, 1);
        for (int dayNumber = 1; dayNumber <= days; dayNumber++) {
            final int targetDay = dayNumber;
            var entries = shown.stream().filter(a -> a.hasTime() && a.getDay() == targetDay)
                    .sorted(Comparator.comparing(ScheduleAssignment::getStartTime).thenComparing(ScheduleAssignment::getEndTime)).toList();
            List<LocalTime> laneEnds = new ArrayList<>(); Map<Integer, Integer> lanes = new HashMap<>();
            for (var entry : entries) {
                int lane = 0;
                while (lane < laneEnds.size() && laneEnds.get(lane).isAfter(entry.getStartTime())) lane++;
                if (lane == laneEnds.size()) laneEnds.add(entry.getEndTime()); else laneEnds.set(lane, entry.getEndTime());
                lanes.put(entry.getId(), lane);
            }
            int laneCount = Math.max(1, laneEnds.size());
            ColumnConstraints column = new ColumnConstraints(); column.setMinWidth(Math.max(170, laneCount * 145)); column.setHgrow(Priority.ALWAYS);
            week.getColumnConstraints().add(column);
            week.add(label(dayLabel(DayOfWeek.of(targetDay)), "schedule-day-heading"), targetDay, 0);
            Pane dayPane = new Pane(); dayPane.setId("day-" + targetDay); dayPane.setPrefHeight(height); dayPane.setMinHeight(height);
            dayPane.getStyleClass().add("schedule-day");
            for (int minute = firstMinute; minute < lastMinute; minute += 30) {
                Region line = new Region(); line.getStyleClass().add("schedule-time-line");
                line.prefWidthProperty().bind(dayPane.widthProperty()); line.setPrefHeight(1);
                line.relocate(0, (minute - firstMinute) * MINUTE_HEIGHT); line.setMouseTransparent(true); dayPane.getChildren().add(line);
            }
            for (var entry : entries) {
                Button item = card(entry);
                item.prefWidthProperty().bind(dayPane.widthProperty().divide(laneCount).subtract(5));
                item.layoutXProperty().bind(dayPane.widthProperty().divide(laneCount).multiply(lanes.get(entry.getId())).add(2));
                item.setLayoutY((entry.getStartTime().toSecondOfDay() / 60.0 - firstMinute) * MINUTE_HEIGHT);
                item.setPrefHeight(Duration.between(entry.getStartTime(), entry.getEndTime()).toMinutes() * MINUTE_HEIGHT - 3);
                item.setMinHeight(0); item.setMaxHeight(Region.USE_PREF_SIZE); dayPane.getChildren().add(item);
            }
            dayPane.setOnMouseClicked(event -> { if (editable) newSession(DayOfWeek.of(targetDay), timeAt(dayPane.sceneToLocal(event.getSceneX(), event.getSceneY()).getY(), firstMinute)); });
            dayPane.setOnDragOver(event -> {
                var source = editable ? dragged(event.getDragboard()) : null;
                if (source != null) {
                    LocalTime time = timeAt(dayPane.sceneToLocal(event.getSceneX(), event.getSceneY()).getY(), firstMinute);
                    event.acceptTransferModes(TransferMode.MOVE); dayPane.getStyleClass().removeAll("schedule-drop-ok", "schedule-drop-error");
                    dayPane.getStyleClass().add(moveProblems(source, targetDay, time).isEmpty() ? "schedule-drop-ok" : "schedule-drop-error");
                }
                event.consume();
            });
            dayPane.setOnDragExited(event -> dayPane.getStyleClass().removeAll("schedule-drop-ok", "schedule-drop-error"));
            dayPane.setOnDragDropped(event -> {
                var source = editable ? dragged(event.getDragboard()) : null;
                LocalTime time = timeAt(dayPane.sceneToLocal(event.getSceneX(), event.getSceneY()).getY(), firstMinute);
                boolean accepted = source != null && moveProblems(source, targetDay, time).isEmpty();
                if (accepted) move(source, targetDay, time); else if (source != null) message.setText(String.join("\n", moveProblems(source, targetDay, time)));
                event.setDropCompleted(accepted); event.consume();
            });
            week.add(dayPane, targetDay, 1);
        }
        long missing = catalogs.groups().stream().filter(g -> g.sessions() != null && countSessions(g.id()) < g.sessions()).count();
        long unresolved = assignments.stream().filter(a -> !a.hasTime()).count();
        summary.setText(shown.size() + " de " + assignments.size() + " sesiones visibles · " + missing + " grupos pendientes"
                + (unresolved == 0 ? "" : " · " + unresolved + " sesiones SIN HORAS: revísalas en Lista"));
    }

    private static LocalTime timeAt(double y, int firstMinute) {
        int minutes = Math.max(7 * 60, Math.min(21 * 60 - 15, firstMinute + (int)(y / MINUTE_HEIGHT / 15) * 15));
        return LocalTime.of(minutes / 60, minutes % 60);
    }

    private Button card(ScheduleAssignment assignment) {
        VBox text = new VBox(3);
        Button card = new Button(); card.setGraphic(text); card.setMaxWidth(Double.MAX_VALUE); card.setId("assignment-" + assignment.getId());
        card.setMinWidth(0);
        text.setMinWidth(0);
        text.prefWidthProperty().bind(card.widthProperty().subtract(24));
        String[] lines = {assignment.getSubject().getNombre(),
                format(assignment.getStartTime()) + " – " + format(assignment.getEndTime()),
                assignment.getGroup().getNombre() + " · " + assignment.getClassroom().getCodigo(), assignment.getTeacher().getName()};
        for (int i = 0; i < lines.length; i++) {
            Text line = new Text(lines[i]);
            line.getStyleClass().add(i == 0 ? "schedule-card-title" : "schedule-card-detail");
            line.wrappingWidthProperty().bind(card.widthProperty().subtract(24));
            text.getChildren().add(line);
        }
        card.getStyleClass().addAll("schedule-card", "schedule-course-" + Math.floorMod(assignment.getGroup().getId(), 5));
        if (assignment.getId().equals(editingId)) card.getStyleClass().add("schedule-card-selected");
        card.setOnAction(event -> {
            if (editable) edit(assignment);
            else Dialogs.info(this, "Detalle de la sesión", assignment.getSubject().getNombre() + "\n" + assignment.getGroup().getNombre()
                    + " · " + assignment.getTeacher().getName() + "\n" + dayLabel(DayOfWeek.of(assignment.getDay())) + " "
                    + format(assignment.getStartTime()) + "–" + format(assignment.getEndTime()) + "\nAula " + assignment.getClassroom().getCodigo());
        }); card.setOnMouseClicked(event -> event.consume());
        card.setOnDragDetected(event -> { if (!editable) return; ClipboardContent data = new ClipboardContent(); data.putString("session:" + selectedVersionId() + ":" + assignment.getId());
            card.startDragAndDrop(TransferMode.MOVE).setContent(data); event.consume(); });
        Tooltip.install(card, new Tooltip(editable ? "Pulsa para editar. Arrastra para mover la sesión a otra hora o día." : "Pulsa para ver el detalle.")); return card;
    }

    private void edit(ScheduleAssignment assignment) {
        applying = true; editingId = assignment.getId(); editorTitle.setText("Editar sesión"); save.setText("Aplicar cambios");
        group.setValue(catalogs.groups().stream().filter(g -> g.id().equals(assignment.getGroup().getId())).findFirst().orElse(null));
        select(subject, assignment.getSubject().getId()); select(teacher, assignment.getTeacher().getId());
        day.setValue(DayOfWeek.of(assignment.getDay())); startTime.setText(format(assignment.getStartTime())); endTime.setText(format(assignment.getEndTime())); applying = false;
        applyGroupRules(false); updateRooms();
        classroom.setValue(classroom.getItems().stream().filter(r -> r.id().equals(assignment.getClassroom().getId())).findFirst().orElse(null)); preview(); render();
    }

    private void newSession(DayOfWeek targetDay, LocalTime targetTime) {
        applying = true; editingId = null; editorTitle.setText("Nueva sesión"); save.setText("Agregar sesión");
        day.setValue(targetDay == null ? DayOfWeek.MONDAY : targetDay);
        startTime.setText(format(targetTime == null ? LocalTime.of(8, 0) : targetTime)); classroom.setValue(null);
        applying = false; applyGroupRules(true); updateRooms(); render();
    }

    private void proposeEnd() {
        LocalTime start = readTime(startTime);
        int minutes = subject.getValue() == null || subject.getValue().minutes() == null ? 60 : subject.getValue().minutes();
        if (start != null) {
            applying = true;
            endTime.setText(start.toSecondOfDay() / 60 + minutes <= 21 * 60 ? format(start.plusMinutes(minutes)) : "");
            applying = false;
        }
    }

    private void applyGroupRules(boolean reset) {
        GroupOption selected = group.getValue(); applying = true;
        subject.setDisable(selected != null && selected.subjectId() != null); teacher.setDisable(selected != null && selected.teacherId() != null);
        if (selected != null && selected.subjectId() != null) select(subject, selected.subjectId()); else if (reset) subject.setValue(null);
        if (selected != null && selected.teacherId() != null) teacher.setValue(new Option(selected.teacherId(), selected.teacherName())); else if (reset) teacher.setValue(null);
        groupProgress.setText(selected == null ? "Elige un grupo para completar sus datos." : selected.sessions() == null
                ? "Grupo anterior sin asignatura vinculada. Selecciona la asignatura y el docente." : countSessions(selected.id()) + " / " + selected.sessions()
                + " sesiones · " + selected.minutes() + " min por sesión · cupo " + selected.requiredSeats()); applying = false;
        if (reset) proposeEnd();
    }

    private void updateRooms() {
        Integer selected = classroom.getValue() == null ? null : classroom.getValue().id();
        int seats = group.getValue() == null ? 0 : group.getValue().requiredSeats();
        int weekday = day.getValue() == null ? 0 : day.getValue().getValue();
        LocalTime start = readTime(startTime), end = readTime(endTime);
        var eligible = catalogs.classrooms().stream().filter(r -> r.capacity() >= seats).filter(r -> assignments.stream().noneMatch(a ->
                !a.getId().equals(editingId) && a.getDay() == weekday && a.getClassroom().getId().equals(r.id())
                && TimeIntervals.overlaps(a.getStartTime(), a.getEndTime(), start, end))).toList();
        applying = true; classroom.getItems().setAll(eligible);
        classroom.setValue(eligible.stream().filter(r -> r.id().equals(selected)).findFirst().orElse(null)); applying = false; preview();
    }

    private List<String> problems() {
        List<String> result = new ArrayList<>();
        if (selectedVersionId() == null) result.add("Crea o selecciona una versión de horario.");
        if (group.getValue() == null) result.add("Selecciona un grupo.");
        if (id(subject) == null || id(teacher) == null) result.add("Completa la asignatura y el docente.");
        if (group.getValue() != null && Boolean.FALSE.equals(group.getValue().teacherActive())) result.add("El docente del grupo está inactivo. Actualízalo en Oferta académica.");
        Placement placement = candidate();
        try { TimeIntervals.validate(placement.start(), placement.end()); }
        catch (IllegalArgumentException error) { result.add("Usa un intervalo válido en HH:mm, dentro de 07:00–21:00."); }
        if (day.getValue() == null) result.add("Selecciona un día.");
        if (placement.start() != null && placement.end() != null && subject.getValue() != null && subject.getValue().minutes() != null
                && Duration.between(placement.start(), placement.end()).toMinutes() != subject.getValue().minutes())
            result.add("La asignatura requiere " + subject.getValue().minutes() + " minutos por sesión.");
        result.addAll(availabilityProblems(placement));
        if (classroom.getValue() == null) result.add(classroom.getItems().isEmpty() ? "No hay aulas disponibles con capacidad suficiente y libres durante todo el intervalo."
                : "Selecciona una de las " + classroom.getItems().size() + " aulas compatibles y libres.");
        result.addAll(service.conflicts(assignments, editingId, placement)); return result;
    }

    private List<String> availabilityProblems(Placement placement) {
        if (placement.teacherId() == null || placement.start() == null || placement.end() == null) return List.of();
        var available = catalogs.availability().stream().filter(a -> a.teacherId().equals(placement.teacherId()) && a.day() == placement.day())
                .map(a -> new TimeIntervals.Range(a.start(), a.end())).toList();
        return TimeIntervals.covered(available, placement.start(), placement.end()) ? List.of()
                : List.of("El intervalo no está cubierto por la disponibilidad del docente. Horas del día: "
                + (available.isEmpty() ? "sin disponibilidad registrada" : available.stream()
                .filter(r -> r.start() != null && r.end() != null)
                .map(r -> format(r.start()) + "–" + format(r.end())).collect(java.util.stream.Collectors.joining(", "))) + ".");
    }

    private void preview() {
        var problems = problems();
        feedback.setText(problems.isEmpty() ? "Intervalo disponible y sin cruces en los datos cargados. Se revalidará al guardar." : String.join("\n", problems));
        feedback.getStyleClass().removeAll("field-error", "schedule-valid"); feedback.getStyleClass().add(problems.isEmpty() ? "schedule-valid" : "field-error");
        save.setDisable(!problems.isEmpty()); remove.setDisable(editingId == null);
    }
    private Placement candidate() { return new Placement(day.getValue() == null ? 0 : day.getValue().getValue(), readTime(startTime), readTime(endTime),
            group.getValue() == null ? null : group.getValue().id(), id(subject), id(teacher), classroom.getValue() == null ? null : classroom.getValue().id()); }
    private void saveSession() {
        if (!problems().isEmpty()) { preview(); return; }
        int version = selectedVersionId(); Integer assignment = editingId; Placement placement = candidate();
        execute(() -> { service.saveAssignment(version, assignment, placement); return true; }, ignored -> reload(version), this::showError);
    }
    private void deleteSession() {
        if (editingId == null || selectedVersionId() == null) return;
        int version = selectedVersionId(), assignment = editingId;
        Dialogs.confirm(this, "Eliminar sesión", "¿Eliminar esta sesión de la versión seleccionada?", () ->
                execute(() -> { service.deleteAssignment(version, assignment); return true; }, ignored -> reload(version), this::showError));
    }
    private List<String> moveProblems(ScheduleAssignment assignment, int day, LocalTime start) {
        Placement placement = placement(assignment, day, start);
        try { TimeIntervals.validate(placement.start(), placement.end()); }
        catch (IllegalArgumentException error) { return List.of("La sesión debe quedar dentro de 07:00–21:00."); }
        List<String> problems = new ArrayList<>(service.conflicts(assignments, assignment.getId(), placement));
        problems.addAll(availabilityProblems(placement)); return problems;
    }
    private void move(ScheduleAssignment assignment, int day, LocalTime start) {
        Placement placement = placement(assignment, day, start);
        var conflicts = moveProblems(assignment, day, start);
        if (!conflicts.isEmpty()) { message.setText(String.join("\n", conflicts)); return; }
        int version = selectedVersionId(); execute(() -> { service.saveAssignment(version, assignment.getId(), placement); return true; }, ignored -> reload(version), this::showError);
    }
    private ScheduleAssignment dragged(Dragboard dragboard) {
        if (!dragboard.hasString()) return null; String prefix = "session:" + selectedVersionId() + ":";
        if (!dragboard.getString().startsWith(prefix)) return null;
        return assignments.stream().filter(a -> (prefix + a.getId()).equals(dragboard.getString())).findFirst().orElse(null);
    }
    private static Placement placement(ScheduleAssignment a, int day, LocalTime start) {
        long minutes = Duration.between(a.getStartTime(), a.getEndTime()).toMinutes();
        LocalTime end = start.toSecondOfDay() / 60 + minutes < 1440 ? start.plusMinutes(minutes) : null;
        return new Placement(day, start, end, a.getGroup().getId(), a.getSubject().getId(), a.getTeacher().getId(), a.getClassroom().getId());
    }

    private void versionDialog(boolean copy, ScheduleVersion selected) {
        TextField name = new TextField(selected == null ? "" : selected.getName() + (copy ? " · copia" : "")); name.setId("schedule-version-name");
        Label error = wrapped("", "field-error");
        Dialogs.form(this, copy ? "Duplicar versión" : selected == null ? "Nueva versión" : "Renombrar versión", new VBox(9, new Label("Nombre de la versión"), name, error), (pane, close) -> {
            String entered = name.getText(); pane.setDisable(true);
            execute(() -> copy ? service.copyVersion(selected.getId(), entered) : service.saveVersion(selected == null ? null : selected.getId(), entered),
                    id -> { pane.setDisable(false); close.run(); reload(id); }, exception -> { pane.setDisable(false); error.setText(errorMessage(exception)); });
        });
    }
    private void deleteVersion() {
        if (versions.getValue() == null) return; int version = selectedVersionId();
        Dialogs.confirm(this, "Eliminar versión", "¿Eliminar esta versión? Debe estar sin sesiones.", () ->
                execute(() -> { service.deleteVersion(version); return true; }, ignored -> reload(null), this::showError));
    }
    private <T> void execute(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        busy.set(true); message.setText("Consultando PostgreSQL…");
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        task.setOnSucceeded(event -> { busy.set(false); onSuccess.accept(task.getValue()); }); task.setOnFailed(event -> { busy.set(false); onError.accept(task.getException()); });
        Thread worker = new Thread(task, "horarios-hibernate"); worker.setDaemon(true); worker.start();
    }
    private void showError(Throwable exception) {
        System.getLogger(ScheduleView.class.getName())
                .log(System.Logger.Level.ERROR,
                        "Error al guardar o cargar el horario", exception);
        message.setText(errorMessage(exception));
    }
    private static String errorMessage(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException || cause instanceof ValidationException) return cause.getMessage();
            if (cause instanceof org.hibernate.exception.ConstraintViolationException) return "El recurso cambió o quedó ocupado. Actualiza la vista y vuelve a intentarlo.";
        }
        return "No se pudo completar la operación. Revisa la conexión y actualiza la vista.";
    }
    private long countSessions(Integer group) { return assignments.stream().filter(a -> a.hasTime() && a.getGroup().getId().equals(group)).count(); }
    private Integer selectedVersionId() { return versions.getValue() == null ? null : versions.getValue().getId(); }
    private static LocalTime readTime(TextField input) { try { return TimeIntervals.parse(input.getText()); } catch (RuntimeException error) { return null; } }
    private static String format(LocalTime time) { return time == null ? "" : time.format(TimeIntervals.FORMAT); }
    private static Integer id(ComboBox<Option> chooser) { return chooser.getValue() == null ? null : chooser.getValue().id(); }
    private static void select(ComboBox<Option> chooser, Integer id) { chooser.setValue(chooser.getItems().stream().filter(o -> o.id().equals(id)).findFirst().orElse(null)); }
    private static Button button(String text, Runnable action) { Button result = new Button(text); result.setOnAction(event -> action.run()); return result; }
    private static Label label(String text, String style) { Label result = new Label(text); result.getStyleClass().add(style); return result; }
    private static Label wrapped(String text, String style) { Label result = label(text, style); result.setWrapText(true); result.setMinWidth(0); return result; }
    private static String dayLabel(DayOfWeek day) { return day.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-NI")); }
    private static TableColumn<ScheduleAssignment, String> column(String title, Function<ScheduleAssignment, String> value) {
        TableColumn<ScheduleAssignment, String> column = new TableColumn<>(title); column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue()))); return column;
    }
    private static <T> StringConverter<T> display(Function<T, String> label) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : label.apply(value); }
            @Override public T fromString(String value) { throw new UnsupportedOperationException(); }
        };
    }
}
