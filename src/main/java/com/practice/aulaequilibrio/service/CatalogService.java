package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.CatalogDAO;
import com.practice.aulaequilibrio.model.Availability;
import com.practice.aulaequilibrio.model.Teacher;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;
import com.practice.aulaequilibrio.util.TimeIntervals;
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

    public Teacher saveTeacher(Integer id, String name, String email, boolean active) {
        return dao.saveTeacher(id, name, email, active);
    }

    public void deleteTeacher(Integer id) {
        dao.deleteTeacher(id);
    }

    public List<Availability> listAvailability(Integer teacherId) {
        return dao.listAvailability(teacherId);
    }

    public Availability saveInterval(Integer id, Integer teacherId, Integer day, String start, String end) {
        try {
            return dao.saveInterval(id, teacherId, day, TimeIntervals.parse(start), TimeIntervals.parse(end));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Escribe las horas en formato HH:mm, por ejemplo 08:30.");
        }
    }

    public void deleteAvailability(Integer id) {
        dao.deleteAvailability(id);
    }
}
