package com.practice.aulaequilibrio.model;

public enum AuditAction {
    INITIAL_ADMIN_CREATED("Administrador inicial"), LOGIN_SUCCESS("Inicio de sesión"), LOGIN_FAILED("Acceso rechazado"),
    LOGOUT("Cierre de sesión"), ACCOUNT_CREATED("Cuenta creada"), ACCOUNT_UPDATED("Cuenta modificada"),
    PASSWORD_RESET("Contraseña restablecida"), PASSWORD_CHANGED("Contraseña cambiada"), ACCOUNT_UNLOCKED("Cuenta desbloqueada");
    private final String label;
    AuditAction(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
