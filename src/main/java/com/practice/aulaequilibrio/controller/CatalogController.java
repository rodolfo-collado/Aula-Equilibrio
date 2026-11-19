package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.service.CatalogService;
import com.practice.aulaequilibrio.model.Availability;
import com.practice.aulaequilibrio.model.Teacher;
import com.practice.aulaequilibrio.util.Dialogs;
import com.practice.aulaequilibrio.util.UiAccess;
import com.practice.aulaequilibrio.model.Permission;
import com.practice.aulaequilibrio.view.mdi.MdiDesktop;
import lombok.RequiredArgsConstructor;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.hibernate.exception.ConstraintViolationException;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class CatalogController {
    private static final List<String> DAYS = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");
    private static final String TEACHER_FORM = "Formulario de docente";
    private static final String ALL_DAYS = "Todos los días";
    private static final String WITH_EMAIL = "Con correo";
    private static final String WITHOUT_EMAIL = "Sin correo";
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    @FXML private SplitPane catalogs;
    @FXML private VBox availabilityPanel;
    @FXML private TableView<Teacher> teachers;
    @FXML private TableColumn<Teacher, String> teacherName, teacherEmail, teacherState;
    @FXML private TableView<Availability> intervals;
    @FXML private TableColumn<Availability, String> intervalDay;
    @FXML private TableColumn<Availability, String> intervalValue;
    @FXML private ComboBox<String> day, emailFilter, dayFilter;
    @FXML private TextField startTime, endTime, teacherSearch;
    @FXML private Label availabilityTitle, intervalEditorTitle, emptyTeachers, emptyIntervals, status;
    @FXML private Button editTeacherButton, deleteTeacherButton, deleteIntervalButton;

    private final CatalogService service;
    private final SimpleBooleanProperty busy = new SimpleBooleanProperty();
    private final ObservableList<Teacher> teacherRows = FXCollections.observableArrayList();
    private final ObservableList<Availability> intervalRows = FXCollections.observableArrayList();
    private final FilteredList<Teacher> filteredTeachers = new FilteredList<>(teacherRows);
    private final FilteredList<Availability> filteredIntervals = new FilteredList<>(intervalRows);
    private MdiDesktop desktop;
    private String moduleTitle;
    private boolean refreshingTeachers;

    public CatalogController() {
        this(new CatalogService());
    }

    public void attachDesktop(MdiDesktop desktop, String moduleTitle) {
        this.desktop = desktop;
        this.moduleTitle = moduleTitle;
    }

    @FXML
    private void initialize() {
        UiAccess.apply(catalogs, Permission.EDIT_TEACHERS);
        teacherName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        teacherEmail.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getEmail()));
        intervalDay.setCellValueFactory(cell -> new ReadOnlyStringWrapper(DAYS.get(cell.getValue().getDay() - 1)));
        teacherState.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().isActive() ? "Activo" : "Inactivo"));
        intervalValue.setCellValueFactory(cell -> {
            Availability value = cell.getValue();
            String text = value.getStartTime() == null ? "Pendiente de definir"
                    : value.getStartTime() + " – " + value.getEndTime();
            return new ReadOnlyStringWrapper(text);
        });
        teachers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        intervals.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        day.getItems().setAll(DAYS);
        configureFilters();

        var noTeacher = teachers.getSelectionModel().selectedItemProperty().isNull();
        catalogs.disableProperty().bind(busy);
        editTeacherButton.disableProperty().bind(noTeacher);
        deleteTeacherButton.disableProperty().bind(noTeacher);
        availabilityPanel.disableProperty().bind(noTeacher);
        deleteIntervalButton.disableProperty().bind(intervals.getSelectionModel().selectedItemProperty().isNull());
        teachers.getSelectionModel().selectedItemProperty().addListener((observable, previous, teacher) -> {
            if (!refreshingTeachers) {
                showIntervals(teacher, List.of());
                if (teacher != null) {
                    run("Disponibilidad actualizada.", () -> service.listAvailability(teacher.getId()),
                            result -> showIntervals(teacher, result), this::message);
                }
            }
        });
        intervals.getSelectionModel().selectedItemProperty().addListener((observable, previous, interval) -> {
            if (interval == null) {
                intervalEditorTitle.setText("Nuevo intervalo");
                day.getSelectionModel().clearSelection();
                startTime.clear();
                endTime.clear();
            } else {
                intervalEditorTitle.setText("Editar intervalo");
                day.getSelectionModel().select(interval.getDay() - 1);
                startTime.setText(Objects.toString(interval.getStartTime(), ""));
                endTime.setText(Objects.toString(interval.getEndTime(), ""));
            }
        });
        reload();
    }

    private void configureFilters() {
        var sortedTeachers = new SortedList<>(filteredTeachers);
        sortedTeachers.comparatorProperty().bind(teachers.comparatorProperty());
        teachers.setItems(sortedTeachers);
        var sortedIntervals = new SortedList<>(filteredIntervals);
        sortedIntervals.comparatorProperty().bind(intervals.comparatorProperty());
        intervals.setItems(sortedIntervals);

        emailFilter.getItems().setAll("Todos", WITH_EMAIL, WITHOUT_EMAIL);
        emailFilter.getSelectionModel().selectFirst();
        dayFilter.getItems().add(ALL_DAYS);
        dayFilter.getItems().addAll(DAYS);
        dayFilter.getSelectionModel().selectFirst();

        teacherSearch.textProperty().addListener((observable, before, after) -> filterTeachers());
        emailFilter.valueProperty().addListener((observable, before, after) -> filterTeachers());
        dayFilter.valueProperty().addListener((observable, before, after) -> filterIntervals());
    }

    private void filterTeachers() {
        String query = searchable(teacherSearch.getText().strip());
        String emailChoice = emailFilter.getValue();
        Teacher selected = teachers.getSelectionModel().getSelectedItem();
        refreshingTeachers = true;
        filteredTeachers.setPredicate(teacher -> {
            String email = Objects.toString(teacher.getEmail(), "");
            boolean hasEmail = !email.isBlank();
            boolean matchesEmail = (!WITH_EMAIL.equals(emailChoice) || hasEmail)
                    && (!WITHOUT_EMAIL.equals(emailChoice) || !hasEmail);
            return matchesEmail && (searchable(teacher.getName()).contains(query) || searchable(email).contains(query));
        });
        if (selected != null && filteredTeachers.contains(selected)) {
            teachers.getSelectionModel().select(selected);
        } else {
            teachers.getSelectionModel().clearSelection();
            showIntervals(null, List.of());
        }
        refreshingTeachers = false;
        emptyTeachers.setText(query.isEmpty() && "Todos".equals(emailChoice)
                ? "No hay docentes registrados." : "Ningún docente coincide con los filtros.");
    }

    private void filterIntervals() {
        int selectedDay = Math.max(0, dayFilter.getSelectionModel().getSelectedIndex());
        Availability selected = intervals.getSelectionModel().getSelectedItem();
        filteredIntervals.setPredicate(interval -> selectedDay == 0 || interval.getDay() == selectedDay);
        if (selected != null && filteredIntervals.contains(selected)) {
            intervals.getSelectionModel().select(selected);
        } else {
            intervals.getSelectionModel().clearSelection();
        }
        updateIntervalPlaceholder();
    }

    private void updateIntervalPlaceholder() {
        if (teachers.getSelectionModel().isEmpty()) {
            emptyIntervals.setText("Selecciona un docente para ver sus intervalos.");
        } else if (intervalRows.isEmpty()) {
            emptyIntervals.setText("Sin intervalos registrados.");
        } else {
            emptyIntervals.setText("Ningún intervalo coincide con los filtros.");
        }
    }

    private static String searchable(String text) {
        return DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                .replaceAll("").toLowerCase(Locale.ROOT);
    }

    @FXML private void newTeacher() {
        openTeacherForm(null);
    }

    @FXML private void editTeacher() {
        Teacher teacher = teachers.getSelectionModel().getSelectedItem();
        if (teacher != null) {
            openTeacherForm(teacher);
        }
    }

    private void openTeacherForm(Teacher teacher) {
        if (desktop.activate(TEACHER_FORM)) {
            return;
        }
        boolean editing = teacher != null;
        TextField name = new TextField(editing ? teacher.getName() : "");
        TextField email = new TextField(editing ? teacher.getEmail() : "");
        name.setPromptText("Nombre del docente");
        email.setPromptText("correo@ejemplo.com");
        Label title = new Label(editing ? "Editar docente" : "Nuevo docente");
        title.getStyleClass().add("page-title");
        CheckBox active = new CheckBox("Activo");
        active.setSelected(!editing || teacher.isActive());
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("field-error");
        Button cancel = new Button("Cancelar");
        cancel.setOnAction(event -> desktop.close(TEACHER_FORM));
        Button save = new Button("Guardar docente");
        save.getStyleClass().add("primary");
        save.disableProperty().bind(busy);
        HBox actions = new HBox(8, cancel, save);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox form = new VBox(10, title, new Label("Nombre *"), name,
                new Label("Correo opcional"), email, active, feedback, actions);
        form.getStyleClass().add("module-form");

        save.setOnAction(event -> {
            String enteredName = name.getText();
            String enteredEmail = email.getText();
            Integer id = editing ? teacher.getId() : null;
            boolean enabled = active.isSelected();
            feedback.setText("");
            run("Docente guardado.", () -> service.saveTeacher(id, enteredName, enteredEmail, enabled), saved -> {
                updateTeacherRow(saved);
                if (catalogs.getScene() != null) {
                    desktop.close(TEACHER_FORM);
                }
            }, feedback::setText);
        });
        desktop.openPopup(moduleTitle, TEACHER_FORM, form, 460, 375);
        name.requestFocus();
    }

    private void updateTeacherRow(Teacher saved) {
        refreshingTeachers = true;
        teacherRows.removeIf(teacher -> Objects.equals(teacher.getId(), saved.getId()));
        teacherRows.add(saved);
        teacherRows.sort(Comparator.comparing(Teacher::getName).thenComparing(Teacher::getId));
        teachers.getSelectionModel().clearSelection();
        refreshingTeachers = false;
        showIntervals(null, List.of());
        if (filteredTeachers.contains(saved)) {
            teachers.getSelectionModel().select(saved);
        }
    }

    @FXML private void deleteTeacher() {
        Teacher teacher = teachers.getSelectionModel().getSelectedItem();
        if (teacher != null) {
            Dialogs.confirm(catalogs, "Eliminar docente", "¿Eliminar al docente «" + teacher.getName() + "»?",
                    () -> run("Docente eliminado.", () -> {
                service.deleteTeacher(teacher.getId());
                return teacher;
            }, removed -> {
                refreshingTeachers = true;
                teachers.getSelectionModel().clearSelection();
                teacherRows.remove(removed);
                refreshingTeachers = false;
                showIntervals(null, List.of());
            }, this::message));
        }
    }

    @FXML private void newInterval() {
        intervals.getSelectionModel().clearSelection();
        day.getSelectionModel().clearSelection();
        startTime.clear();
        endTime.clear();
        day.requestFocus();
    }

    @FXML private void saveInterval() {
        Teacher owner = teachers.getSelectionModel().getSelectedItem();
        if (owner == null) {
            return;
        }
        Availability selected = intervals.getSelectionModel().getSelectedItem();
        Integer id = selected == null ? null : selected.getId();
        Integer dayValue = day.getValue() == null ? null : DAYS.indexOf(day.getValue()) + 1;
        String start = startTime.getText();
        String end = endTime.getText();
        run("Disponibilidad guardada.", () -> service.saveInterval(id, owner.getId(), dayValue, start, end), saved -> {
            intervals.getSelectionModel().clearSelection();
            intervalRows.removeIf(interval -> Objects.equals(interval.getId(), saved.getId()));
            intervalRows.add(saved);
            intervalRows.sort(Comparator.comparing(Availability::getDay)
                    .thenComparing(Availability::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Availability::getId));
            updateIntervalPlaceholder();
            day.getSelectionModel().clearSelection();
            startTime.clear();
            endTime.clear();
        }, this::message);
    }

    @FXML private void deleteInterval() {
        Availability interval = intervals.getSelectionModel().getSelectedItem();
        if (interval != null) {
            Dialogs.confirm(catalogs, "Eliminar intervalo", "¿Eliminar el intervalo seleccionado?",
                    () -> run("Intervalo eliminado.", () -> {
                service.deleteAvailability(interval.getId());
                return interval;
            }, removed -> {
                intervals.getSelectionModel().clearSelection();
                intervalRows.remove(removed);
                updateIntervalPlaceholder();
            }, this::message));
        }
    }

    @FXML private void reload() {
        Teacher selected = teachers.getSelectionModel().getSelectedItem();
        Integer selectedId = selected == null ? null : selected.getId();
        run("Docentes actualizados.", service::listTeachers, items -> {
            refreshingTeachers = true;
            teacherRows.setAll(items);
            teachers.getSelectionModel().clearSelection();
            refreshingTeachers = false;
            showIntervals(null, List.of());
            filteredTeachers.stream().filter(teacher -> Objects.equals(teacher.getId(), selectedId))
                    .findFirst().ifPresent(teachers.getSelectionModel()::select);
        }, this::message);
    }

    private void showIntervals(Teacher teacher, List<Availability> items) {
        availabilityTitle.setText(teacher == null ? "Selecciona un docente" : teacher.getName());
        intervals.getSelectionModel().clearSelection();
        intervalRows.setAll(items);
        updateIntervalPlaceholder();
        day.getSelectionModel().clearSelection();
        startTime.clear();
        endTime.clear();
    }

    private <T> void run(String success, Callable<T> work, Consumer<T> apply, Consumer<String> showError) {
        busy.set(true);
        status.setText("Consultando PostgreSQL…");
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            status.setText(success);
            apply.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy.set(false);
            Throwable failure = task.getException();
            String text = (failure instanceof IllegalArgumentException || failure instanceof com.practice.aulaequilibrio.util.ValidationException) ? failure.getMessage()
                    : failure instanceof ConstraintViolationException ? "Ya existe un registro con esos datos."
                    : "No se pudo completar la operación. Revisa la conexión con PostgreSQL.";
            status.setText(text);
            showError.accept(text);
        });
        Thread worker = new Thread(task, "catalogos-hibernate");
        worker.setDaemon(true);
        worker.start();
    }

    private void message(String text) {
        Dialogs.warning(catalogs, text);
    }
}
