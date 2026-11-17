package com.practice.aulaequilibrio.model;

import java.util.List;

public final class ScheduleData {
    private ScheduleData() {
    }

    public record Option(Integer id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record Catalogs(List<Option> groups, List<Option> subjects, List<Option> teachers, List<Option> classrooms) {
    }

    public record Placement(int day, int block, Integer groupId, Integer subjectId, Integer teacherId,
                            Integer classroomId) {
    }
}
