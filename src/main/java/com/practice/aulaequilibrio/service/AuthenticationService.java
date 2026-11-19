package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.AuthenticationDAO;
import com.practice.aulaequilibrio.model.AccountData.Identity;
import com.practice.aulaequilibrio.util.*;
import lombok.RequiredArgsConstructor;
import java.util.Arrays;
import java.util.Locale;

@RequiredArgsConstructor
public class AuthenticationService {
    private final AuthenticationDAO dao;
    public AuthenticationService() { this(new AuthenticationDAO()); }
    public void prepareRoles() { dao.ensureRoles(); }
    public boolean needsSetup() { return dao.needsSetup(); }
    public Identity createInitialAdmin(String username, String name, String email, char[] password) {
        try {
            return dao.createInitialAdmin(username(username), Validations.required(name, "nombre", 120), email(email), PasswordHasher.hash(password));
        } finally { clear(password); }
    }
    public Identity login(String username, char[] password) {
        try {
            Identity result = dao.authenticate(username(username), password);
            if (result == null) throw new ValidationException("Credenciales incorrectas o cuenta no disponible. Revisa los datos o consulta al administrador.");
            return result;
        } finally { clear(password); }
    }
    public void logout(String token) { dao.logout(token); }
    public Identity changePassword(String token, char[] current, char[] next) {
        try { return dao.changePassword(token, current, next, PasswordHasher.hash(next)); }
        finally { clear(current); clear(next); }
    }
    static String username(String value) {
        String name = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9][a-z0-9._-]{2,39}")) throw new ValidationException("El usuario debe tener 3–40 caracteres: letras, números, punto, guion o guion bajo.");
        return name;
    }
    static String email(String value) {
        String result = value == null ? "" : value.strip();
        if (result.isEmpty()) return null;
        if (result.length() > 120 || !result.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new ValidationException("Escribe un correo válido o deja el campo vacío.");
        return result;
    }
    static void clear(char[] value) { if (value != null) Arrays.fill(value, '\0'); }
}
