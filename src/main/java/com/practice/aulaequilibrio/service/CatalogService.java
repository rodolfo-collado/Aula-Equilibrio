package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.CatalogDAO;
import com.practice.aulaequilibrio.model.Availability;
import com.practice.aulaequilibrio.model.Teacher;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CatalogService {
    private final CatalogDAO dao;

    public CatalogService() {
        this(new CatalogDAO());
    }

    public List<Teacher> listTeachers() {
        return dao.listTeachers();
    }

    public Teacher saveTeacher(Integer id, String name, String email) {
        return dao.saveTeacher(id, name, email);
    }

    public void deleteTeacher(Integer id) {
        dao.deleteTeacher(id);
    }

    public List<Availability> listAvailability(Integer teacherId) {
        return dao.listAvailability(teacherId);
    }

    public Availability saveAvailability(Integer id, Integer teacherId, Integer day, Integer blockNumber) {
        return dao.saveAvailability(id, teacherId, day, blockNumber);
    }

    public void deleteAvailability(Integer id) {
        dao.deleteAvailability(id);
    }
}
