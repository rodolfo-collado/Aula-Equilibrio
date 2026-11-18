package com.practice.aulaequilibrio.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import org.hibernate.annotations.Check;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "schedule_assignment")
@Check(name = "ck_schedule_interval", constraints = "(start_time is null and end_time is null) or (start_time is not null and end_time is not null and start_time < end_time and start_time >= TIME '07:00' and end_time <= TIME '21:00')")
public class ScheduleAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, updatable = false)
    private ScheduleVersion version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Groupe group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @Column(name = "day_of_week", nullable = false)
    private int day;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    public boolean hasTime() { return startTime != null && endTime != null && startTime.isBefore(endTime); }

    public ScheduleAssignment(ScheduleVersion version) {
        this.version = version;
    }

    public void assign(Groupe group, Subject subject, Teacher teacher, Classroom classroom, int day, LocalTime startTime, LocalTime endTime) {
        this.group = group;
        this.subject = subject;
        this.teacher = teacher;
        this.classroom = classroom;
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
