package com.practice.aulaequilibrio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "grupo")
public class Groupe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 40)
    @Setter
    private String nombre;

    @Column(name = "cantidad_estudiantes", nullable = false)
    @Setter
    private Integer cantidadEstudiantes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    @Setter
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    @Setter
    private Teacher teacher;

    @Column(name = "capacity", nullable = false, columnDefinition = "integer default 30")
    @Setter
    private int capacity = 30;

    @Override
    public String toString() {
        return nombre;
    }
}
