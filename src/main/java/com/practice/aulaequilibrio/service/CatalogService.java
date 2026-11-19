package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.CatalogDAO;
import com.practice.aulaequilibrio.model.Availability;
import com.practice.aulaequilibrio.model.Teacher;
import lombok.RequiredArgsConstructor;
import com.practice.aulaequilibrio.model.Permission;

import com.practice.aulaequilibrio.util.TimeIntervals;
import java.util.List;

@RequiredArgsConstructor
public class CatalogService {
    private final CatalogDAO dao;
    private final AccessControl access;

    public CatalogService(CatalogDAO dao) { this(dao, AccessControl.current()); }

    public CatalogService() {
        this(new CatalogDAO(), AccessControl.current());
    }

    public List<Teacher> listTeachers() {
        access.require(Permission.READ_ACADEMIC);
        return dao.listTeachers();
    }

    public Teacher saveTeacher(Integer id, String name, String email, boolean active) {
        access.require(Permission.EDIT_TEACHERS);
        return dao.saveTeacher(id, name, email, active);
    }

    public void deleteTeacher(Integer id) {
        access.require(Permission.EDIT_TEACHERS);
        dao.deleteTeacher(id);
    }

    public List<Availability> listAvailability(Integer teacherId) {
        access.require(Permission.READ_ACADEMIC);
        return dao.listAvailability(teacherId);
    }

    public Availability saveInterval(Integer id, Integer teacherId, Integer day, String start, String end) {
        access.require(Permission.EDIT_TEACHERS);
        try {
            return dao.saveInterval(id, teacherId, day, TimeIntervals.parse(start), TimeIntervals.parse(end));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Escribe las horas en formato HH:mm, por ejemplo 08:30.");
        }
    }

    public void deleteAvailability(Integer id) {
        access.require(Permission.EDIT_TEACHERS);
        dao.deleteAvailability(id);
    }
}
