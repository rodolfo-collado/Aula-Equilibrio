package com.practice.aulaequilibrio.model;

import java.util.Set;
import static com.practice.aulaequilibrio.model.Permission.*;

public enum RoleCode {
    ADMINISTRADOR("Administrador", Set.of(Permission.values())),
    COORDINADOR("Coordinador", Set.of(READ_ACADEMIC, EDIT_STRUCTURE, EDIT_TEACHERS, EDIT_OFFER, EDIT_CLASSROOMS, EDIT_SCHEDULES, SEND_SCHEDULE_EMAIL)),
    PLANIFICADOR("Planificador", Set.of(READ_ACADEMIC, EDIT_SCHEDULES)),
    CONSULTA("Consulta", Set.of(READ_ACADEMIC)),
    AUDITOR("Auditor", Set.of(READ_ACADEMIC, VIEW_AUDIT));

    private final String label;
    private final Set<Permission> permissions;
    RoleCode(String label, Set<Permission> permissions) { this.label = label; this.permissions = permissions; }
    public boolean allows(Permission permission) { return permissions.contains(permission); }
    @Override public String toString() { return label; }
}
