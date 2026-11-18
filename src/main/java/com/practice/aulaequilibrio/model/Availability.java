package com.practice.aulaequilibrio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import org.hibernate.annotations.Check;

/** Un intervalo disponible de un docente; día ISO: lunes=1, domingo=7. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "availability")
@Check(name = "ck_availability_interval", constraints = "(start_time is null and end_time is null) or (start_time is not null and end_time is not null and start_time < end_time and start_time >= TIME '07:00' and end_time <= TIME '21:00')")
public class Availability {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    @Setter
    private Teacher teacher;

    @Column(name = "weekday", nullable = false)
    @Setter
    private Integer day;

    @Column(name = "start_time")
    @Setter
    private LocalTime startTime;

    @Column(name = "end_time")
    @Setter
    private LocalTime endTime;
}
