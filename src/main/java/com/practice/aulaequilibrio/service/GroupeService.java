package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.model.Groupe;
import com.practice.aulaequilibrio.dao.GroupeDAO;
import com.practice.aulaequilibrio.util.ErrorMessages;
import com.practice.aulaequilibrio.util.ValidationException;
import com.practice.aulaequilibrio.util.Validations;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class GroupeService {

    private static final int MAX_NOMBRE = 40;

    private final GroupeDAO dao;

    public GroupeService() {
        this(new GroupeDAO());
    }

    public List<Groupe> listAll() {
        return dao.findAll();
    }

    /**
     * Crea un grupo nuevo ({@code id == null}) o actualiza uno existente.
     */
    public Groupe save(Integer id, String nombre, String cantidadEstudiantes) {
        String cleanNombre = Validations.required(nombre, "nombre del grupo", MAX_NOMBRE);
        int cantidad = Validations.positiveInteger(cantidadEstudiantes, "cantidad de estudiantes");

        try {
            if (dao.existsByNombre(cleanNombre, id)) {
                throw new ValidationException("Ya existe un grupo con el nombre \"" + cleanNombre + "\".");
            }

            Groupe groupe = id == null
                    ? new Groupe()
                    : dao.findById(id).orElseThrow(() ->
                    new ValidationException("El grupo ya no existe. Actualice la lista."));
            groupe.setNombre(cleanNombre);
            groupe.setCantidadEstudiantes(cantidad);
            return dao.save(groupe);
        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ErrorMessages.onSave("el grupo", exception);
        }
    }

    public void delete(Integer id) {
        if (id == null) {
            throw new ValidationException("Seleccione un grupo para eliminar.");
        }
        try {
            if (!dao.deleteById(id)) {
                throw new ValidationException("El grupo ya no existe. Actualice la lista.");
            }
        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ErrorMessages.onDelete("el grupo", exception);
        }
    }
}
