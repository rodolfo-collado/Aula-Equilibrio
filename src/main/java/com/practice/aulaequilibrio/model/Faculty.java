package com.practice.aulaequilibrio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "faculty")
@Getter
@NoArgsConstructor
public class Faculty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Setter
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Setter
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Setter
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Getter(lombok.AccessLevel.NONE)
    @OneToMany(mappedBy = "faculty", cascade = CascadeType.PERSIST)
    private List<Career> careers = new ArrayList<>();

    public List<Career> getCareers() {
        return Collections.unmodifiableList(careers);
    }

    public void addCareer(Career career) {
        career.setFaculty(this);
    }

    void attachCareer(Career career) {
        if (!careers.contains(career)) {
            careers.add(career);
        }
    }

    void detachCareer(Career career) {
        careers.remove(career);
    }
}
