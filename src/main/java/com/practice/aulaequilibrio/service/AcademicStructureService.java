package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.AcademicStructureDAO;
import com.practice.aulaequilibrio.model.Career;
import com.practice.aulaequilibrio.model.CareerSubject;
import com.practice.aulaequilibrio.model.Faculty;
import com.practice.aulaequilibrio.model.FacultySummary;
import com.practice.aulaequilibrio.util.ValidationException;
import com.practice.aulaequilibrio.util.Validations;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
public class AcademicStructureService {
    private final AcademicStructureDAO dao;

    public AcademicStructureService() {
        this(new AcademicStructureDAO());
    }

    public List<FacultySummary> listFaculties() {
        return dao.listFaculties();
    }

    public List<Career> listCareers(Long facultyId) {
        return dao.listCareers(facultyId);
    }

    public List<CareerSubject> listStudyPlan(long careerId) {
        return dao.listStudyPlan(careerId);
    }

    public Faculty saveFaculty(Long id, String code, String name, boolean active) {
        return dao.saveFaculty(id, normalizeCode(code),
                Validations.required(name, "nombre de la facultad", 120), active);
    }

    public Career saveCareer(Long id, long facultyId, String code, String name, boolean active) {
        return dao.saveCareer(id, facultyId, normalizeCode(code),
                Validations.required(name, "nombre de la carrera", 120), active);
    }

    public void deleteFaculty(long id) {
        dao.deleteFaculty(id);
    }

    public void deleteCareer(long id) {
        dao.deleteCareer(id);
    }

    public void addSubject(long careerId, int subjectId, Short recommendedLevel) {
        validateLevel(recommendedLevel);
        dao.addSubject(careerId, subjectId, recommendedLevel);
    }

    public void updateRecommendedLevel(long careerId, int subjectId, Short recommendedLevel) {
        validateLevel(recommendedLevel);
        dao.updateRecommendedLevel(careerId, subjectId, recommendedLevel);
    }

    public void removeSubject(long careerId, int subjectId) {
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
