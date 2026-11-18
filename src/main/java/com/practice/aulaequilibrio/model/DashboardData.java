package com.practice.aulaequilibrio.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class DashboardData {
    private DashboardData() {}

    public enum Destination { FACULTIES, TEACHERS, OFFER, ROOMS, SCHEDULES }

    public record VersionOption(Integer id, String name, LocalDate createdOn) {
        @Override public String toString() { return name + " · " + createdOn; }
    }

    public record FacultyInfo(Long id, String name, boolean active, Long careers) {}
    public record SubjectLink(Long facultyId, Integer subjectId) {}
    public record GroupInfo(Integer id, Integer subjectId, Integer teacherId, Integer weeklySessions) {}
    public record RoomInfo(Integer id, Integer capacity, ClassroomStatus state) {}
    public record AssignmentInfo(Integer groupId, Integer subjectId, Integer classroomId, Integer day, LocalTime start, LocalTime end) {}
    public record ChartValue(String key, String label, long value) {}

    public record Facts(List<FacultyInfo> faculties, List<SubjectLink> links, List<GroupInfo> groups,
                        List<RoomInfo> rooms, List<VersionOption> versions, VersionOption selectedVersion,
                        List<AssignmentInfo> assignments, long subjects, long teachers, long activeTeachers,
                        long teachersWithoutAvailability, long unlinkedSubjects) {}

    public record FacultyRow(Long id, String name, long careers, long subjects, long groups,
                             long classrooms, long assignments) {}
    public record Notice(String title, String detail, Destination destination) {}
    public record Snapshot(List<VersionOption> versions, VersionOption selectedVersion,
                           long faculties, long careers, long subjects, long groups, long teachers,
                           long activeTeachers, long rooms, long availableRooms, long availableSeats,
                           long assignments, long scheduledGroups, long usedRooms,
                           long requiredSessions, long coveredSessions,
                           List<FacultyRow> facultyRows, List<Notice> notices,
                           List<ChartValue> roomStates, List<ChartValue> sessionsByDay) {
        public double coverage() {
            return requiredSessions == 0 ? 0 : (double) coveredSessions / requiredSessions;
        }
    }
}
