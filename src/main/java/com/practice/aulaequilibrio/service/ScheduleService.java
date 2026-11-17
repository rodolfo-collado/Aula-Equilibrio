package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.ScheduleDAO;
import com.practice.aulaequilibrio.model.ScheduleAssignment;
import com.practice.aulaequilibrio.model.ScheduleData.Catalogs;
import com.practice.aulaequilibrio.model.ScheduleData.Placement;
import com.practice.aulaequilibrio.model.ScheduleVersion;
import lombok.RequiredArgsConstructor;

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
