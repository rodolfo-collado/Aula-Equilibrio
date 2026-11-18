package com.practice.aulaequilibrio.model;

public enum ClassroomStatus {
    DISPONIBLE("Disponible"),
    MANTENIMIENTO("Mantenimiento"),
    INACTIVA("Inactiva");

    private final String label;

    ClassroomStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
