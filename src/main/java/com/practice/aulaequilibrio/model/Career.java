package com.practice.aulaequilibrio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table(name = "career")
@Getter
@NoArgsConstructor
public class Career {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_career")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_faculty", nullable = false)
    private Faculty faculty;

    @Setter
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Setter
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Setter
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public void setFaculty(Faculty faculty) {
        Objects.requireNonNull(faculty, "La carrera debe pertenecer a una facultad.");
        if (this.faculty == faculty) {
            return;
        }
        if (this.faculty != null) {
            this.faculty.detachCareer(this);
        }
        this.faculty = faculty;
        faculty.attachCareer(this);
    }
}
