package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.model.Subject;
import com.practice.aulaequilibrio.dao.SubjectDAO;
import com.practice.aulaequilibrio.util.ErrorMessages;
import com.practice.aulaequilibrio.util.ValidationException;
import com.practice.aulaequilibrio.util.Validations;
import lombok.RequiredArgsConstructor;
import com.practice.aulaequilibrio.model.Permission;

import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
public class SubjectService {

    private static final int MAX_CODIGO = 20;
    private static final int MAX_NOMBRE = 100;

    private final SubjectDAO dao;
    private final AccessControl access;

    public SubjectService(SubjectDAO dao) { this(dao, AccessControl.current()); }

    public SubjectService() {
        this(new SubjectDAO(), AccessControl.current());
    }

    public List<Subject> listAll() {
        access.require(Permission.READ_ACADEMIC);
        return dao.findAll();
    }

    /**
     * Crea una asignatura nueva ({@code id == null}) o actualiza una existente.
     */
    public Subject save(Integer id, String codigo, String nombre, String sesionesRequeridas) {
        access.require(Permission.EDIT_OFFER);
        String cleanCodigo = Validations.required(codigo, "código", MAX_CODIGO).toUpperCase(Locale.ROOT);
        String cleanNombre = Validations.required(nombre, "nombre de la asignatura", MAX_NOMBRE);
        int sesiones = Validations.positiveInteger(sesionesRequeridas, "número de sesiones requeridas");

        try {
            if (dao.existsByCodigo(cleanCodigo, id)) {
                throw new ValidationException("Ya existe una asignatura con el código \"" + cleanCodigo + "\".");
            }

            Subject subject = id == null
                    ? new Subject()
                    : dao.findById(id).orElseThrow(() ->
                    new ValidationException("La asignatura ya no existe. Actualice la lista."));
            subject.setCodigo(cleanCodigo);
            subject.setNombre(cleanNombre);
            subject.setSesionesRequeridas(sesiones);
            return dao.save(subject);
        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ErrorMessages.onSave("la asignatura", exception);
        }
    }

    public void delete(Integer id) {
        access.require(Permission.EDIT_OFFER);
        if (id == null) {
            throw new ValidationException("Seleccione una asignatura para eliminar.");
        }
        try {
            if (!dao.deleteById(id)) {
                throw new ValidationException("La asignatura ya no existe. Actualice la lista.");
            }
        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ErrorMessages.onDelete("la asignatura", exception);
        }
    }
}
