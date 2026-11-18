package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.service.ClassroomService;
import com.practice.aulaequilibrio.util.Dialogs;
import com.practice.aulaequilibrio.util.Validations;
import com.practice.aulaequilibrio.util.ValidationException;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class ClassroomController {
    @FXML private BorderPane root;
    @FXML private TableView<Classroom> classroomTable;
    @FXML private TableView<ClassroomType> typeTable;
    @FXML private TableColumn<Classroom, String> codeColumn, capacityColumn, classroomTypeColumn, stateColumn;
    @FXML private TableColumn<ClassroomType, String> typeNameColumn, descriptionColumn;
    @FXML private Label status, classroomScope;
    @FXML private TextField search;
    @FXML private ComboBox<ClassroomStatus> stateFilter;
    @FXML private Button editClassroomButton, deleteClassroomButton, editTypeButton, deleteTypeButton, showAllButton;

    private final ClassroomService service = new ClassroomService();
    private final ObservableList<Classroom> roomRows = FXCollections.observableArrayList();
    private final FilteredList<Classroom> filtered = new FilteredList<>(roomRows);
    private final SimpleBooleanProperty busy = new SimpleBooleanProperty();
    private boolean refreshing;

    private record Catalogs(List<ClassroomType> types, List<Classroom> rooms) {}

    @FXML private void initialize() {
        codeColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCodigo()));
        capacityColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCapacidad().toString()));
        classroomTypeColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getType().getName()));
        stateColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getState().toString()));
        typeNameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getName()));
        descriptionColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getDescription()));
        classroomTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        typeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        var sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(classroomTable.comparatorProperty());
        classroomTable.setItems(sorted);
        root.disableProperty().bind(busy);
        var noRoom = classroomTable.getSelectionModel().selectedItemProperty().isNull();
        editClassroomButton.disableProperty().bind(noRoom);
        deleteClassroomButton.disableProperty().bind(noRoom);
        var noType = typeTable.getSelectionModel().selectedItemProperty().isNull();
        editTypeButton.disableProperty().bind(noType);
        deleteTypeButton.disableProperty().bind(noType);
        showAllButton.disableProperty().bind(noType);
        stateFilter.getItems().add(null);
        stateFilter.getItems().addAll(ClassroomStatus.values());
        stateFilter.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(ClassroomStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? "Todos los estados" : item.toString());
            }
        });
        search.textProperty().addListener((obs, before, after) -> filter());
        stateFilter.valueProperty().addListener((obs, before, after) -> filter());
        typeTable.getSelectionModel().selectedItemProperty().addListener((obs, before, after) -> {
            if (!refreshing) filter();
        });
        refresh();
    }

    @FXML private void refresh() {
        ClassroomType selected = typeTable.getSelectionModel().getSelectedItem();
        Integer typeId = selected == null ? null : selected.getId();
        run("Catálogos actualizados.", () -> new Catalogs(service.listTypes(), service.listClassrooms(null)), result -> {
            refreshing = true;
            typeTable.getItems().setAll(result.types());
            typeTable.getSelectionModel().clearSelection();
            result.types().stream().filter(t -> t.getId().equals(typeId)).findFirst()
                    .ifPresent(typeTable.getSelectionModel()::select);
            roomRows.setAll(result.rooms());
            refreshing = false;
            filter();
        });
    }

    @FXML private void showAllClassrooms() {
        typeTable.getSelectionModel().clearSelection();
    }

    private void filter() {
        ClassroomType type = typeTable.getSelectionModel().getSelectedItem();
        ClassroomStatus state = stateFilter.getValue();
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        Classroom selected = classroomTable.getSelectionModel().getSelectedItem();
        filtered.setPredicate(room -> (type == null || room.getType().getId().equals(type.getId()))
                && (state == null || room.getState() == state)
                && (room.getCodigo() + " " + room.getType().getName()).toLowerCase(Locale.ROOT).contains(query));
        if (selected != null && filtered.contains(selected)) classroomTable.getSelectionModel().select(selected);
        else classroomTable.getSelectionModel().clearSelection();
        classroomScope.setText(type == null ? "Todos los tipos" : type.getName());
        status.setText(filtered.size() + " aulas visibles · " + roomRows.size() + " registradas");
    }

    @FXML private void addClassroom() { roomForm(null); }
    @FXML private void editSelectedClassroom() { roomForm(classroomTable.getSelectionModel().getSelectedItem()); }

    private void roomForm(Classroom selected) {
        if (typeTable.getItems().isEmpty()) {
            Dialogs.info(root, "Tipos de aula", "Registra primero un tipo de aula en el panel lateral.");
            return;
        }
        TextField code = new TextField(selected == null ? "" : selected.getCodigo());
        TextField capacity = new TextField(selected == null ? "" : selected.getCapacidad().toString());
        ComboBox<ClassroomType> type = new ComboBox<>(FXCollections.observableArrayList(typeTable.getItems()));
        type.setMaxWidth(Double.MAX_VALUE);
        ClassroomType initial = selected == null ? typeTable.getSelectionModel().getSelectedItem() : selected.getType();
        if (initial != null) type.getItems().stream().filter(t -> t.getId().equals(initial.getId()))
                .findFirst().ifPresent(type::setValue);
        ComboBox<ClassroomStatus> state = new ComboBox<>(FXCollections.observableArrayList(ClassroomStatus.values()));
        state.setValue(selected == null ? ClassroomStatus.DISPONIBLE : selected.getState());
        Label feedback = feedback();
        VBox fields = new VBox(8, new Label("Código *"), code, new Label("Capacidad *"), capacity,
                new Label("Tipo *"), type, new Label("Estado *"), state, feedback);
        Dialogs.form(root, selected == null ? "Nueva aula" : "Editar aula", fields, (pane, close) -> {
            try {
                int seats = Validations.positiveInteger(capacity.getText(), "capacidad");
                String enteredCode = code.getText();
                Integer typeId = type.getValue() == null ? null : type.getValue().getId();
                ClassroomStatus enteredState = state.getValue();
                save(pane, close, feedback, () -> service.saveClassroom(selected == null ? null : selected.getId(),
                        selected == null ? 0 : selected.getRevision(), enteredCode, seats, typeId, enteredState));
            } catch (RuntimeException exception) { feedback.setText(message(exception)); }
        });
    }

    @FXML private void addType() { typeForm(null); }
    @FXML private void editSelectedType() { typeForm(typeTable.getSelectionModel().getSelectedItem()); }

    private void typeForm(ClassroomType selected) {
        TextField name = new TextField(selected == null ? "" : selected.getName());
        TextField description = new TextField(selected == null ? "" : Objects.toString(selected.getDescription(), ""));
        Label feedback = feedback();
        VBox fields = new VBox(8, new Label("Nombre *"), name, new Label("Descripción"), description, feedback);
        Dialogs.form(root, selected == null ? "Nuevo tipo de aula" : "Editar tipo de aula", fields, (pane, close) -> {
            String enteredName = name.getText();
            String enteredDescription = description.getText();
            save(pane, close, feedback, () -> service.saveType(selected == null ? null : selected.getId(),
                    selected == null ? 0 : selected.getRevision(), enteredName, enteredDescription));
        });
    }

    private void save(DialogPane pane, Runnable close, Label feedback, Runnable action) {
        pane.setDisable(true);
        feedback.setText("Guardando…");
        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                action.run();
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            pane.setDisable(false);
            close.run();
            refresh();
        });
        task.setOnFailed(event -> {
            pane.setDisable(false);
            feedback.setText(message(task.getException()));
        });
        start(task);
    }

    @FXML private void deleteClassroom() {
        Classroom selected = classroomTable.getSelectionModel().getSelectedItem();
        if (selected != null) remove("¿Eliminar el aula " + selected.getCodigo() + "?", () -> service.deleteClassroom(selected));
    }
    @FXML private void deleteType() {
        ClassroomType selected = typeTable.getSelectionModel().getSelectedItem();
        if (selected != null) remove("¿Eliminar el tipo " + selected.getName() + "?", () -> service.deleteType(selected));
    }

    private void remove(String question, Runnable action) {
        Dialogs.confirm(root, "Confirmar eliminación", question, () -> run("Registro eliminado.", () -> {
            action.run();
            return true;
        }, ignored -> refresh()));
    }

    private <T> void run(String success, Callable<T> work, Consumer<T> apply) {
        busy.set(true);
        status.setText("Consultando PostgreSQL…");
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            status.setText(success);
            apply.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy.set(false);
            status.setText("No se pudo completar la operación.");
            Dialogs.error(root, "Aulas y tipos de aula", message(task.getException()));
        });
        start(task);
    }

    private static void start(Task<?> task) {
        Thread worker = new Thread(task, "aulas-hibernate");
        worker.setDaemon(true);
        worker.start();
    }
    private static Label feedback() {
        Label label = new Label();
        label.setWrapText(true);
        label.getStyleClass().add("field-error");
        return label;
    }
    private static String message(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException || cause instanceof ValidationException) return cause.getMessage();
            if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
                return "El registro está duplicado o tiene referencias. Revisa las aulas y horarios asociados.";
            }
        }
        return "No se pudo completar la operación. Revisa la conexión con PostgreSQL y actualiza la tabla.";
    }
}
