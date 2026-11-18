package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.ScheduleDAO;
import com.practice.aulaequilibrio.model.ScheduleAssignment;
import com.practice.aulaequilibrio.model.ScheduleData.Catalogs;
import com.practice.aulaequilibrio.model.ScheduleData.Placement;
import com.practice.aulaequilibrio.model.ScheduleVersion;
import lombok.RequiredArgsConstructor;
import com.practice.aulaequilibrio.util.TimeIntervals;

import java.util.List;

@RequiredArgsConstructor
public class ScheduleService {
    private final ScheduleDAO dao;

    public ScheduleService() {
        this(new ScheduleDAO());
    }

    public List<ScheduleVersion> listVersions() {
        return dao.listVersions();
    }

    public int saveVersion(Integer id, String name) {
        return dao.saveVersion(id, name);
    }

    public void deleteVersion(int id) {
        dao.deleteVersion(id);
    }

    public int copyVersion(int id, String name) { return dao.copyVersion(id, name); }

    public List<String> conflicts(List<ScheduleAssignment> assignments, Integer ignored, Placement candidate) {
        return assignments.stream().filter(a -> !a.getId().equals(ignored)
                        && a.getDay() == candidate.day() && TimeIntervals.overlaps(a.getStartTime(), a.getEndTime(), candidate.start(), candidate.end()))
                .filter(a -> a.getGroup().getId().equals(candidate.groupId())
                        || a.getTeacher().getId().equals(candidate.teacherId())
                        || a.getClassroom().getId().equals(candidate.classroomId()))
                .map(a -> {
                    var resources = new java.util.ArrayList<String>();
                    if (a.getGroup().getId().equals(candidate.groupId())) resources.add("grupo");
                    if (a.getTeacher().getId().equals(candidate.teacherId())) resources.add("docente");
                    if (a.getClassroom().getId().equals(candidate.classroomId())) resources.add("aula");
                    return "Cruce de " + String.join(", ", resources) + " con " + a.getSubject().getNombre()
                            + " (" + a.getGroup().getNombre() + ", " + a.getClassroom().getCodigo() + ", "
                            + a.getStartTime() + "–" + a.getEndTime() + ").";
                }).toList();
    }

    public List<ScheduleAssignment> listAssignments(int versionId, Integer groupId) {
        return dao.listAssignments(versionId, groupId);
    }

    public Catalogs listCatalogs() {
        return dao.listCatalogs();
    }

    public void saveAssignment(int versionId, Integer assignmentId, Placement placement) {
        dao.saveAssignment(versionId, assignmentId, placement);
    }

    public void deleteAssignment(int versionId, int assignmentId) {
        dao.deleteAssignment(versionId, assignmentId);
    }
}
