package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.model.AccountData.Identity;
import com.practice.aulaequilibrio.util.AuthorizationException;
import com.practice.aulaequilibrio.util.PasswordHasher;
import com.practice.aulaequilibrio.util.ValidationException;
import jakarta.persistence.LockModeType;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.Instant;
import java.time.Duration;
import java.util.function.Supplier;

public class AuthenticationDAO {
    private static final String DUMMY_HASH = PasswordHasher.hash(PasswordHasher.newToken().toCharArray());
    private final Supplier<SessionFactory> factory;
    public AuthenticationDAO() { factory = HibernateUtil::getSessionFactory; }
    public AuthenticationDAO(SessionFactory factory) { this.factory = () -> factory; }
    public SessionFactory factory() { return factory.get(); }

    public void ensureRoles() {
        try {
            factory().inTransaction(s -> {
                for (RoleCode code : RoleCode.values()) {
                    if (s.createSelectionQuery("from UserRole where code = :code", UserRole.class)
                            .setParameter("code", code).getResultList().isEmpty()) s.persist(new UserRole(code));
                }
            });
        } catch (RuntimeException error) {
            long count = factory().fromTransaction(s -> s.createSelectionQuery("select count(r) from UserRole r", Long.class).getSingleResult());
            if (count != RoleCode.values().length) throw error;
        }
    }
    public boolean needsSetup() {
        return factory().fromTransaction(s -> s.createSelectionQuery("select count(a) from UserAccount a", Long.class).getSingleResult() == 0);
    }
    static UserRole role(Session s, RoleCode code, boolean lock) {
        UserRole role = s.createSelectionQuery("from UserRole where code = :code", UserRole.class).setParameter("code", code).getSingleResult();
        if (lock) s.lock(role, LockModeType.PESSIMISTIC_WRITE);
        return role;
    }
    public Identity createInitialAdmin(String username, String name, String email, String hash) {
        return factory().fromTransaction(s -> {
            UserRole role = role(s, RoleCode.ADMINISTRADOR, true);
            if (s.createSelectionQuery("select count(a) from UserAccount a", Long.class).getSingleResult() != 0)
                throw new ValidationException("Ya existe una cuenta. Inicia sesión con un usuario registrado.");
            UserAccount user = new UserAccount(); user.setUsername(username); user.setDisplayName(name); user.setEmail(email);
            user.setRole(role); user.setPasswordHash(hash); user.setLastLogin(Instant.now()); s.persist(user);
            audit(s, user, user, username, AuditAction.INITIAL_ADMIN_CREATED, "Configuración inicial del administrador.");
            return openSession(s, user);
        });
    }
    public Identity authenticate(String username, char[] password) {
        return factory().fromTransaction(s -> {
            UserAccount user = s.createSelectionQuery("from UserAccount a join fetch a.role where a.username = :name", UserAccount.class)
                    .setParameter("name", username).getResultStream().findFirst().orElse(null);
            if (user != null) s.refresh(user, LockModeType.PESSIMISTIC_WRITE);
            boolean matches = user == null ? PasswordHasher.verify(password, DUMMY_HASH) : user.matchesPassword(password);
            Instant now = Instant.now();
            boolean locked = user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
            if (user == null || !user.isActive() || locked || !matches) {
                if (user != null && user.isActive() && !locked) {
                    if (user.getLockedUntil() != null) { user.setFailedAttempts(0); user.setLockedUntil(null); }
                    user.setFailedAttempts(user.getFailedAttempts() + 1);
                    if (user.getFailedAttempts() >= 5) user.setLockedUntil(now.plus(Duration.ofMinutes(15)));
                }
                audit(s, null, user, username, AuditAction.LOGIN_FAILED, "Intento de acceso rechazado.");
                return null;
            }
            user.setFailedAttempts(0); user.setLockedUntil(null); user.setLastLogin(now);
            audit(s, user, user, username, AuditAction.LOGIN_SUCCESS, "Inicio de sesión correcto.");
            return openSession(s, user);
        });
    }
    public static UserAccount require(Session s, String token, Permission permission) {
        if (token == null) throw new AuthorizationException("Inicia sesión para continuar.");
        LoginSession login = s.createSelectionQuery("""
                from LoginSession l join fetch l.account a join fetch a.role
                where l.tokenHash = :token and l.revokedAt is null and l.expiresAt > :now
                """, LoginSession.class).setParameter("token", PasswordHasher.tokenHash(token))
                .setParameter("now", Instant.now()).getResultStream().findFirst().orElse(null);
        if (login == null || !login.getAccount().isActive() || login.getSecurityVersion() != login.getAccount().getSecurityVersion())
            throw new AuthorizationException("La sesión expiró o fue revocada. Vuelve a iniciar sesión.");
        UserAccount user = login.getAccount();
        if (permission != null && user.isMustChangePassword()) throw new AuthorizationException("Cambia tu contraseña antes de continuar.");
        if (permission != null && !user.getRole().getCode().allows(permission)) throw new AuthorizationException("Tu rol no tiene permiso para realizar esta operación.");
        return user;
    }
    public void requireAccess(String token, Permission permission) {
        factory().inTransaction(s -> require(s, token, permission));
    }
    public void logout(String token) {
        if (token == null) return;
        factory().inTransaction(s -> {
            var sessions = s.createSelectionQuery("from LoginSession where tokenHash = :token and revokedAt is null", LoginSession.class)
                    .setParameter("token", PasswordHasher.tokenHash(token)).getResultList();
            for (var login : sessions) {
                login.setRevokedAt(Instant.now());
                audit(s, login.getAccount(), login.getAccount(), "", AuditAction.LOGOUT, "Cierre de sesión.");
            }
        });
    }
    public Identity changePassword(String token, char[] current, char[] next, String hash) {
        return factory().fromTransaction(s -> {
            UserAccount user = require(s, token, null); s.refresh(user, LockModeType.PESSIMISTIC_WRITE);
            if (!user.matchesPassword(current)) throw new ValidationException("La contraseña actual no es correcta.");
            if (user.matchesPassword(next)) throw new ValidationException("La nueva contraseña debe ser diferente de la actual.");
            user.setPasswordHash(hash); user.setMustChangePassword(false); revokeAll(s, user);
            audit(s, user, user, "", AuditAction.PASSWORD_CHANGED, "Cambio de contraseña y revocación de sesiones anteriores.");
            return openSession(s, user);
        });
    }
    static void revokeAll(Session s, UserAccount user) {
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        s.createMutationQuery("update LoginSession set revokedAt = :now where account.id = :id and revokedAt is null")
                .setParameter("now", Instant.now()).setParameter("id", user.getId()).executeUpdate();
    }
    private static Identity openSession(Session s, UserAccount user) {
        String token = PasswordHasher.newToken();
        s.persist(new LoginSession(PasswordHasher.tokenHash(token), user, Instant.now().plus(Duration.ofHours(12))));
        return new Identity(token, user.getUsername(), user.getDisplayName(), user.getRole().getCode(), user.isMustChangePassword());
    }
    static void audit(Session s, UserAccount actor, UserAccount target, String claimed, AuditAction action, String detail) {
        s.persist(new AccountAudit(actor, target, claimed, action, detail));
    }
}
