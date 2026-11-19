package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.AcademicOfferDAO;
import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.util.ErrorMessages;
import com.practice.aulaequilibrio.util.Validations;
import com.practice.aulaequilibrio.util.ValidationException;
import lombok.RequiredArgsConstructor;
import com.practice.aulaequilibrio.model.Permission;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class AcademicOfferService {
    private final AcademicOfferDAO dao;
    private final AccessControl access;

    public AcademicOfferService(AcademicOfferDAO dao) { this(dao, AccessControl.current()); }

    public AcademicOfferService() {
        this(new AcademicOfferDAO(), AccessControl.current());
    }

    public List<Subject> listSubjects() {
        access.require(Permission.READ_ACADEMIC); return dao.listSubjects(); }
    public List<CareerSubject> listCareers(int subjectId) {
        access.require(Permission.READ_ACADEMIC); return dao.listCareers(subjectId); }
    public List<Groupe> listGroups(int subjectId) {
        access.require(Permission.READ_ACADEMIC); return dao.listGroups(subjectId); }

    public Subject saveSubject(Integer id, String code, String name, String hours, String sessions,
                               List<Long> required, List<Long> optional) {
        access.require(Permission.EDIT_OFFER);
        String cleanCode = Validations.required(code, "código", 20).toUpperCase(Locale.ROOT);
        String cleanName = Validations.required(name, "nombre", 100);
        int minutes;
        try {
            minutes = new BigDecimal(hours.strip().replace(',', '.')).multiply(BigDecimal.valueOf(60)).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new ValidationException("La duración debe ser un número de horas equivalente a minutos completos.");
        }
        if (minutes < 1 || minutes > 1440) throw new ValidationException("La duración debe estar entre un minuto y 24 horas.");
        int count = Validations.positiveInteger(sessions, "sesiones semanales");
        if (required.isEmpty() && optional.isEmpty()) throw new ValidationException("Selecciona al menos una carrera.");
        if (required.stream().anyMatch(optional::contains)) throw new ValidationException("Una carrera solo puede tener un tipo de asignatura.");
        return save("la asignatura", () -> dao.saveSubject(id, cleanCode, cleanName, minutes, count,
                List.copyOf(required), List.copyOf(optional)));
    }

    public Groupe saveGroup(Integer id, int subjectId, String name, String capacity, String demand, Integer teacherId) {
        access.require(Permission.EDIT_OFFER);
        String cleanName = Validations.required(name, "código del grupo", 40).toUpperCase(Locale.ROOT);
        int max = Validations.positiveInteger(capacity, "cupo");
        int estimated;
        try {
            estimated = Integer.parseInt(demand.strip());
        } catch (NumberFormatException exception) {
            throw new ValidationException("La demanda debe ser un número entero.");
        }
        if (estimated < 0 || estimated > max) throw new ValidationException("La demanda debe estar entre cero y el cupo.");
        return save("el grupo", () -> dao.saveGroup(id, subjectId, cleanName, max, estimated, teacherId));
    }

    public void deleteSubject(int id) {
        access.require(Permission.EDIT_OFFER); delete("la asignatura", () -> dao.deleteSubject(id)); }
    public void deleteGroup(int id) {
        access.require(Permission.EDIT_OFFER); delete("el grupo", () -> dao.deleteGroup(id)); }

    private static <T> T save(String label, Supplier<T> action) {
        try {
            return action.get();
        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ErrorMessages.onSave(label, exception);
        }
    }

    private static void delete(String label, Runnable action) {
        try {
            action.run();
        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ErrorMessages.onDelete(label, exception);
        }
    }
}
