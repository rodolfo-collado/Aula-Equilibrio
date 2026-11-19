package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.AcademicStructureDAO;
import com.practice.aulaequilibrio.model.Career;
import com.practice.aulaequilibrio.model.CareerSubject;
import com.practice.aulaequilibrio.model.Faculty;
import com.practice.aulaequilibrio.model.FacultySummary;
import com.practice.aulaequilibrio.util.ValidationException;
import com.practice.aulaequilibrio.util.Validations;
import lombok.RequiredArgsConstructor;
import com.practice.aulaequilibrio.model.Permission;

import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
public class AcademicStructureService {
    private final AcademicStructureDAO dao;
    private final AccessControl access;

    public AcademicStructureService(AcademicStructureDAO dao) { this(dao, AccessControl.current()); }

    public AcademicStructureService() {
        this(new AcademicStructureDAO(), AccessControl.current());
    }

    public List<FacultySummary> listFaculties() {
        access.require(Permission.READ_ACADEMIC);
        return dao.listFaculties();
    }

    public List<Career> listCareers(Long facultyId) {
        access.require(Permission.READ_ACADEMIC);
        return dao.listCareers(facultyId);
    }

    public List<CareerSubject> listStudyPlan(long careerId) {
        access.require(Permission.READ_ACADEMIC);
        return dao.listStudyPlan(careerId);
    }

    public Faculty saveFaculty(Long id, String code, String name, boolean active) {
        access.require(Permission.EDIT_STRUCTURE);
        return dao.saveFaculty(id, normalizeCode(code),
                Validations.required(name, "nombre de la facultad", 120), active);
    }

    public Career saveCareer(Long id, long facultyId, String code, String name, boolean active) {
        access.require(Permission.EDIT_STRUCTURE);
        return dao.saveCareer(id, facultyId, normalizeCode(code),
                Validations.required(name, "nombre de la carrera", 120), active);
    }

    public void deleteFaculty(long id) {
        access.require(Permission.EDIT_STRUCTURE);
        dao.deleteFaculty(id);
    }

    public void deleteCareer(long id) {
        access.require(Permission.EDIT_STRUCTURE);
        dao.deleteCareer(id);
    }

    public void addSubject(long careerId, int subjectId, Short recommendedLevel) {
        access.require(Permission.EDIT_STRUCTURE);
        validateLevel(recommendedLevel);
        dao.addSubject(careerId, subjectId, recommendedLevel);
    }

    public void updateRecommendedLevel(long careerId, int subjectId, Short recommendedLevel) {
        access.require(Permission.EDIT_STRUCTURE);
        validateLevel(recommendedLevel);
        dao.updateRecommendedLevel(careerId, subjectId, recommendedLevel);
    }

    public void removeSubject(long careerId, int subjectId) {
        access.require(Permission.EDIT_STRUCTURE);
        dao.removeSubject(careerId, subjectId);
    }

    private static String normalizeCode(String code) {
        String normalized = code == null ? null : code.toUpperCase(Locale.ROOT);
        return Validations.required(normalized, "código", 20);
    }

    private static void validateLevel(Short level) {
        if (level != null && level < 1) {
            throw new ValidationException("El nivel recomendado debe ser mayor que cero.");
        }
    }
}
