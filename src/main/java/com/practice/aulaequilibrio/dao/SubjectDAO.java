package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.model.Subject;

import java.util.List;

public class SubjectDAO extends IDAO<Subject> {

    public SubjectDAO() {
        super(Subject.class, Subject::getId);
    }

    public List<Subject> findAll() {
        return findAll("codigo");
    }

    public boolean existsByCodigo(String codigo, Integer excludeId) {
        return existsByFieldIgnoreCase("codigo", codigo, excludeId);
    }
}
