package com.practice.aulaequilibrio.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table(name = "career_subject")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareerSubject {
    @EmbeddedId
    private CareerSubjectId id = new CareerSubjectId();

    @MapsId("careerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_career", nullable = false)
    private Career career;

    @MapsId("subjectId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_subject", nullable = false)
    private Subject subject;

    @Setter
    @Column(name = "recommended_level")
    private Short recommendedLevel;

    @Column(name = "optional", nullable = false, columnDefinition = "boolean default false")
    @Setter
    private boolean optional;

    public CareerSubject(Career career, Subject subject, Short recommendedLevel) {
        this.career = Objects.requireNonNull(career, "La carrera es obligatoria.");
        this.subject = Objects.requireNonNull(subject, "La asignatura es obligatoria.");
        this.recommendedLevel = recommendedLevel;
    }
}
