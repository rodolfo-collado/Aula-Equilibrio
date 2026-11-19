package com.practice.aulaequilibrio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_role")
@Getter
@NoArgsConstructor
public class UserRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, unique = true, length = 24)
    private RoleCode code;
    public UserRole(RoleCode code) { this.code = code; }
    @Override public String toString() { return code.toString(); }
}
