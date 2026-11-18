package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.service.*;
import com.practice.aulaequilibrio.util.Dialogs;
import com.practice.aulaequilibrio.util.ErrorMessages;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

public class AcademicOfferController {
    @FXML private VBox root, groupPanel;
    @FXML private TableView<Subject> subjects;
    @FXML private TableView<Groupe> groups;
    @FXML private TableColumn<Subject, String> subjectCode, subjectName, subjectDuration, subjectSessions;
    @FXML private TableColumn<Groupe, String> groupCode, groupCapacity, groupDemand, groupTeacher;
    @FXML private Label groupTitle;
    @FXML private Button editSubject, deleteSubject, editGroup, deleteGroup;
    @FXML private TextField search;
    @FXML private ComboBox<Career> careerFilter;

    private final AcademicOfferService offer = new AcademicOfferService();
    private final AcademicStructureService academic = new AcademicStructureService();
    private final CatalogService catalog = new CatalogService();
    private final ObservableList<Subject> rows = FXCollections.observableArrayList();
    private final FilteredList<Subject> filtered = new FilteredList<>(rows);
    private Set<Integer> careerSubjects = Set.of();

    @FXML private void initialize() {
        subjectCode.setCellValueFactory(c -> text(c.getValue().getCodigo()));
        subjectName.setCellValueFactory(c -> text(c.getValue().getNombre()));
        subjectDuration.setCellValueFactory(c -> text(hours(c.getValue()) + " h"));
        subjectSessions.setCellValueFactory(c -> text(c.getValue().getSesionesRequeridas()));
        groupCode.setCellValueFactory(c -> text(c.getValue().getNombre()));
        groupCapacity.setCellValueFactory(c -> text(c.getValue().getCapacity()));
        groupDemand.setCellValueFactory(c -> text(c.getValue().getCantidadEstudiantes()));
        groupTeacher.setCellValueFactory(c -> text(c.getValue().getTeacher() == null
                ? "Sin asignar" : c.getValue().getTeacher().getName()));
        subjects.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        groups.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        var sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(subjects.comparatorProperty());
        subjects.setItems(sorted);
        var noSubject = subjects.getSelectionModel().selectedItemProperty().isNull();
        groupPanel.disableProperty().bind(noSubject);
        editSubject.disableProperty().bind(noSubject);
        deleteSubject.disableProperty().bind(noSubject);
        editGroup.disableProperty().bind(groups.getSelectionModel().selectedItemProperty().isNull());
        deleteGroup.disableProperty().bind(editGroup.disableProperty());
        subjects.getSelectionModel().selectedItemProperty().addListener((obs, before, after) -> loadGroups(after));
        careerFilter.setConverter(converter(c -> c.getCode() + " · " + c.getName(), "Todas las carreras"));
        careerFilter.valueProperty().addListener((obs, before, after) -> filterByCareer());
        search.textProperty().addListener((obs, before, after) -> applyFilter());
        Platform.runLater(this::reload);
    }

    private static ReadOnlyStringWrapper text(Object value) { return new ReadOnlyStringWrapper(String.valueOf(value)); }
    private static String hours(Subject subject) {
        return BigDecimal.valueOf(subject.getDurationMinutes()).divide(BigDecimal.valueOf(60), 4,
                java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    @FXML private void reload() {
        try {
            Integer selected = subjects.getSelectionModel().getSelectedItem() == null ? null
                    : subjects.getSelectionModel().getSelectedItem().getId();
            Career selectedCareer = careerFilter.getValue();
            careerFilter.getItems().clear();
            careerFilter.getItems().add(null);
            careerFilter.getItems().addAll(academic.listCareers(null));
            if (selectedCareer != null) careerFilter.getItems().stream().filter(Objects::nonNull)
                    .filter(c -> c.getId().equals(selectedCareer.getId())).findFirst().ifPresent(careerFilter::setValue);
            rows.setAll(offer.listSubjects());
            filterByCareer();
            filtered.stream().filter(s -> s.getId().equals(selected)).findFirst()
                    .ifPresent(subjects.getSelectionModel()::select);
        } catch (RuntimeException exception) {
            Dialogs.error(root, "Cargar oferta académica", exception);
        }
    }

    private void filterByCareer() {
        try {
            Career career = careerFilter.getValue();
            careerSubjects = career == null ? Set.of() : new HashSet<>(academic.listStudyPlan(career.getId())
                    .stream().map(link -> link.getSubject().getId()).toList());
            applyFilter();
        } catch (RuntimeException exception) {
            Dialogs.error(root, "Filtrar por carrera", exception);
        }
    }

    private void applyFilter() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        filtered.setPredicate(s -> (careerFilter.getValue() == null || careerSubjects.contains(s.getId()))
                && (s.getCodigo() + " " + s.getNombre()).toLowerCase(Locale.ROOT).contains(query));
        if (subjects.getSelectionModel().getSelectedItem() == null) loadGroups(null);
    }

    private void loadGroups(Subject subject) {
        groups.getItems().clear();
        groupTitle.setText(subject == null ? "Selecciona una asignatura" : subject.getNombre());
        if (subject != null) {
            try {
                groups.getItems().setAll(offer.listGroups(subject.getId()));
            } catch (RuntimeException exception) {
                Dialogs.error(root, "Cargar grupos", exception);
            }
        }
    }

    @FXML private void addSubject() { subjectForm(null); }
    @FXML private void editSubject() { subjectForm(subjects.getSelectionModel().getSelectedItem()); }

    private void subjectForm(Subject selected) {
        try {
            List<Career> careers = academic.listCareers(null);
            if (careers.isEmpty()) {
                Dialogs.warning(root, "Registra primero una facultad y una carrera.");
                return;
            }
            TextField code = new TextField(selected == null ? "" : selected.getCodigo());
            TextField name = new TextField(selected == null ? "" : selected.getNombre());
            TextField duration = new TextField(selected == null ? "2" : hours(selected));
            TextField sessions = new TextField(selected == null ? "2" : selected.getSesionesRequeridas().toString());
            Map<Long, CareerSubject> links = new HashMap<>();
            if (selected != null) offer.listCareers(selected.getId()).forEach(l -> links.put(l.getCareer().getId(), l));
            Map<Long, ComboBox<String>> choices = new LinkedHashMap<>();
            VBox careerRows = new VBox(7);
            for (Career career : careers) {
                ComboBox<String> choice = new ComboBox<>(FXCollections.observableArrayList("No pertenece", "Obligatoria", "Opcional"));
                CareerSubject link = links.get(career.getId());
                choice.getSelectionModel().select(link == null ? 0 : link.isOptional() ? 2 : 1);
                choices.put(career.getId(), choice);
                Label label = new Label(career.getCode() + " · " + career.getName());
                label.setWrapText(true);
                careerRows.getChildren().add(new HBox(8, choice, label));
            }
            ScrollPane plans = new ScrollPane(careerRows);
            plans.setFitToWidth(true);
            plans.setPrefViewportHeight(Math.min(180, careers.size() * 40));
            Label feedback = feedback();
            VBox form = new VBox(8, new Label("Código *"), code, new Label("Nombre *"), name,
                    new Label("Duración por sesión (horas) *"), duration,
                    new Label("Sesiones por semana *"), sessions, new Label("Carreras *"), plans, feedback);
            Dialogs.form(root, selected == null ? "Nueva asignatura" : "Editar asignatura", form, (pane, close) -> {
                try {
                    List<Long> required = choices.entrySet().stream().filter(e -> e.getValue().getSelectionModel().getSelectedIndex() == 1)
                            .map(Map.Entry::getKey).toList();
                    List<Long> optional = choices.entrySet().stream().filter(e -> e.getValue().getSelectionModel().getSelectedIndex() == 2)
                            .map(Map.Entry::getKey).toList();
                    Subject saved = offer.saveSubject(selected == null ? null : selected.getId(), code.getText(),
                            name.getText(), duration.getText(), sessions.getText(), required, optional);
                    close.run();
                    reload();
                    filtered.stream().filter(s -> s.getId().equals(saved.getId())).findFirst()
                            .ifPresent(subjects.getSelectionModel()::select);
                } catch (RuntimeException exception) { feedback.setText(ErrorMessages.forUser(exception)); }
            });
        } catch (RuntimeException exception) { Dialogs.error(root, "Abrir asignatura", exception); }
    }

    @FXML private void addGroup() { groupForm(null); }
    @FXML private void editGroup() { groupForm(groups.getSelectionModel().getSelectedItem()); }

    private void groupForm(Groupe selected) {
        Subject subject = subjects.getSelectionModel().getSelectedItem();
        if (subject == null) return;
        try {
            TextField name = new TextField(selected == null ? "" : selected.getNombre());
            TextField capacity = new TextField(selected == null ? "30" : String.valueOf(selected.getCapacity()));
            TextField demand = new TextField(selected == null ? "0" : String.valueOf(selected.getCantidadEstudiantes()));
            ComboBox<Teacher> teacher = new ComboBox<>();
            teacher.getItems().add(null);
            teacher.getItems().addAll(catalog.listTeachers().stream().filter(Teacher::isActive).toList());
            teacher.setConverter(converter(t -> t.getName(), "Sin asignar"));
            if (selected != null && selected.getTeacher() != null) teacher.getItems().stream().filter(Objects::nonNull)
                    .filter(t -> t.getId().equals(selected.getTeacher().getId())).findFirst().ifPresent(teacher::setValue);
            Label feedback = feedback();
            VBox form = new VBox(8, new Label(subject.toString()), new Label("Código del grupo *"), name,
                    new Label("Cupo máximo *"), capacity, new Label("Demanda estimada *"), demand,
                    new Label("Profesor (opcional)"), teacher, feedback);
            Dialogs.form(root, selected == null ? "Nuevo grupo" : "Editar grupo", form, (pane, close) -> {
                try {
                    offer.saveGroup(selected == null ? null : selected.getId(), subject.getId(), name.getText(),
                            capacity.getText(), demand.getText(), teacher.getValue() == null ? null : teacher.getValue().getId());
                    close.run();
                    loadGroups(subject);
                } catch (RuntimeException exception) { feedback.setText(ErrorMessages.forUser(exception)); }
            });
        } catch (RuntimeException exception) { Dialogs.error(root, "Abrir grupo", exception); }
    }

    @FXML private void deleteSubject() {
        Subject subject = subjects.getSelectionModel().getSelectedItem();
        if (subject != null) confirmDelete("asignatura", () -> { offer.deleteSubject(subject.getId()); reload(); });
    }
    @FXML private void deleteGroup() {
        Groupe group = groups.getSelectionModel().getSelectedItem();
        if (group != null) confirmDelete("grupo", () -> {
            offer.deleteGroup(group.getId());
            loadGroups(subjects.getSelectionModel().getSelectedItem());
        });
    }
    private void confirmDelete(String label, Runnable action) {
        Dialogs.confirm(root, "Eliminar " + label, "¿Eliminar el registro seleccionado?", () -> {
            try { action.run(); } catch (RuntimeException exception) { Dialogs.error(root, "Eliminar " + label, exception); }
        });
    }
    private static Label feedback() {
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("field-error");
        return feedback;
    }
    private static <T> StringConverter<T> converter(Function<T, String> label, String empty) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? empty : label.apply(value); }
            @Override public T fromString(String value) { throw new UnsupportedOperationException(); }
        };
    }
}
