package com.practice.aulaequilibrio.model;

import java.util.List;
import java.time.LocalTime;

public final class ScheduleData {
    private ScheduleData() {
    }

    public record Option(Integer id, String label, Integer minutes) {
        public Option(Integer id, String label) { this(id, label, null); }
        @Override
        public String toString() {
            return label;
        }
    }

    public record GroupOption(Integer id, String name, Integer subjectId, String subjectName,
                              Integer teacherId, String teacherName, Boolean teacherActive,
                              Integer sessions, Integer minutes, Integer capacity, Integer demand) {
        public int requiredSeats() { return subjectId == null ? demand : capacity; }
        @Override public String toString() { return name + (subjectName == null ? " · sin asignatura" : " · " + subjectName); }
    }

    public record RoomOption(Integer id, String code, Integer capacity, String type) {
        @Override public String toString() { return code + " · " + capacity + " plazas · " + type; }
    }

    public record AvailabilityOption(Integer teacherId, Integer day, LocalTime start, LocalTime end) {}
    public record Catalogs(List<GroupOption> groups, List<Option> subjects, List<Option> teachers,
                           List<RoomOption> classrooms, List<AvailabilityOption> availability) {}

    public record Placement(int day, LocalTime start, LocalTime end, Integer groupId, Integer subjectId, Integer teacherId,
                            Integer classroomId) {
    }
}
