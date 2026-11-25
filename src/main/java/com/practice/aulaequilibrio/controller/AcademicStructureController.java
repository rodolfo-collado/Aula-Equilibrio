package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.model.Career;
import com.practice.aulaequilibrio.model.FacultySummary;
import com.practice.aulaequilibrio.service.AcademicStructureService;
import com.practice.aulaequilibrio.util.Dialogs;
import com.practice.aulaequilibrio.util.UiAccess;
import com.practice.aulaequilibrio.model.Permission;
import com.practice.aulaequilibrio.util.ErrorMessages;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.BiConsumer;

public class AcademicStructureController {
    @FXML private VBox root, careerPanel;
    @FXML private TableView<FacultySummary> faculties;
    @FXML private TableView<Career> careers;
    @FXML private TableColumn<FacultySummary, String> facultyCode, facultyName, facultyState;
    @FXML private TableColumn<Career, String> careerCode, careerName, careerState;
    @FXML private Button editFaculty, deleteFaculty, editCareer, deleteCareer;
    private final AcademicStructureService service = new AcademicStructureService();

    @FXML private void initialize() {
        UiAccess.apply(root, Permission.EDIT_STRUCTURE);
        facultyCode.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().code()));
        facultyName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        facultyState.setCellValueFactory(c -> new ReadOnlyStringWrapper(state(c.getValue().active())));
        careerCode.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCode()));
        careerName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getName()));
        careerState.setCellValueFactory(c -> new ReadOnlyStringWrapper(state(c.getValue().isActive())));
        var noFaculty = faculties.getSelectionModel().selectedItemProperty().isNull();
        careerPanel.disableProperty().bind(noFaculty);
        editFaculty.disableProperty().bind(noFaculty);
        deleteFaculty.disableProperty().bind(noFaculty);
        editCareer.disableProperty().bind(careers.getSelectionModel().selectedItemProperty().isNull());
        deleteCareer.disableProperty().bind(editCareer.disableProperty());
        faculties.getSelectionModel().selectedItemProperty().addListener((obs, before, after) -> loadCareers(after));
        Platform.runLater(() -> reload(null));
    }

    private static String state(boolean active) {
        return active ? "Activa" : "Inactiva";
    }

    @FXML private void refreshFaculties() {
        FacultySummary faculty = faculties.getSelectionModel().getSelectedItem();
        reload(faculty == null ? null : faculty.id());
    }

    @FXML private void refreshCareers() {
        loadCareers(faculties.getSelectionModel().getSelectedItem());
    }

    private void reload(Long facultyId) {
        try {
            faculties.getItems().setAll(service.listFaculties());
            faculties.getSelectionModel().clearSelection();
            faculties.getItems().stream().filter(f -> Objects.equals(f.id(), facultyId))
                    .findFirst().ifPresent(faculties.getSelectionModel()::select);
        } catch (RuntimeException exception) {
            Dialogs.error(root, "Cargar facultades", exception);
        }
    }

    private void loadCareers(FacultySummary faculty) {
        careers.getItems().clear();
        if (faculty != null) {
            try {
                careers.getItems().setAll(service.listCareers(faculty.id()));
            } catch (RuntimeException exception) {
                Dialogs.error(root, "Cargar carreras", exception);
            }
        }
    }

    @FXML private void addFaculty() { facultyForm(null); }
    @FXML private void editFaculty() { facultyForm(faculties.getSelectionModel().getSelectedItem()); }
    @FXML private void addCareer() { careerForm(null); }
    @FXML private void editCareer() { careerForm(careers.getSelectionModel().getSelectedItem()); }

    private void facultyForm(FacultySummary faculty) {
        editor(faculty == null ? "Nueva facultad" : "Editar facultad",
                faculty == null ? "" : faculty.code(), faculty == null ? "" : faculty.name(),
                faculty == null || faculty.active(), (values, done) -> {
                    var saved = service.saveFaculty(faculty == null ? null : faculty.id(),
                            values.code(), values.name(), values.active());
                    done.run();
                    reload(saved.getId());
                });
    }

    private void careerForm(Career career) {
        FacultySummary faculty = faculties.getSelectionModel().getSelectedItem();
        if (faculty == null) {
            return;
        }
        editor(career == null ? "Nueva carrera" : "Editar carrera",
                career == null ? "" : career.getCode(), career == null ? "" : career.getName(),
                career == null || career.isActive(), (values, done) -> {
                    service.saveCareer(career == null ? null : career.getId(), faculty.id(),
                            values.code(), values.name(), values.active());
                    done.run();
                    loadCareers(faculty);
                });
    }

    @FXML private void deleteFaculty() {
        FacultySummary faculty = faculties.getSelectionModel().getSelectedItem();
        if (faculty != null) {
            delete("Eliminar facultad", faculty.name(), () -> {
                service.deleteFaculty(faculty.id());
                reload(null);
            });
        }
    }

    @FXML private void deleteCareer() {
        Career career = careers.getSelectionModel().getSelectedItem();
        var faculty = faculties.getSelectionModel().getSelectedItem();
        if (career != null) {
            delete("Eliminar carrera", career.getName(), () -> {
                service.deleteCareer(career.getId());
                loadCareers(faculty);
            });
        }
    }

    private void delete(String title, String name, Runnable action) {
        Dialogs.confirm(root, title, "¿Eliminar «" + name + "»?", () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                Dialogs.error(root, title, exception);
            }
        });
    }

    private record Values(String code, String name, boolean active) {}

    private void editor(String title, String code, String name, boolean active, BiConsumer<Values, Runnable> save) {
        TextField codeField = new TextField(code);
        TextField nameField = new TextField(name);
        CheckBox activeField = new CheckBox("Activa");
        activeField.setSelected(active);
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("field-error");
        VBox fields = new VBox(8, new Label("Código *"), codeField, new Label("Nombre *"), nameField,
                activeField, feedback);
        Dialogs.form(root, title, fields, (pane, close) -> {
            try {
                save.accept(new Values(codeField.getText(), nameField.getText(), activeField.isSelected()), close);
            } catch (RuntimeException exception) {
                feedback.setText(ErrorMessages.forUser(exception));
            }
        });
    }
}
