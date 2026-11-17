package com.practice.aulaequilibrio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CareerSubjectId implements Serializable {
    @Column(name = "id_career")
    private Long careerId;

    @Column(name = "id_subject")
    private Integer subjectId;
}
