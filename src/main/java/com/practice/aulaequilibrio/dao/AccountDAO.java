package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.model.AccountData.*;
import com.practice.aulaequilibrio.util.ValidationException;
import jakarta.persistence.LockModeType;
import java.util.List;

public class AccountDAO {
    private final AuthenticationDAO auth;
    public AccountDAO() { this(new AuthenticationDAO()); }
    public AccountDAO(AuthenticationDAO auth) { this.auth = auth; }
    public List<AccountRow> list(String token) {
        return auth.factory().fromTransaction(s -> {
            AuthenticationDAO.require(s, token, Permission.MANAGE_ACCOUNTS);
            return s.createSelectionQuery("""
                    select a.id, a.revision, a.username, a.displayName, a.email, r.code, a.active,
                           a.mustChangePassword, a.lockedUntil, a.lastLogin
                    from UserAccount a join a.role r order by a.username
                    """, AccountRow.class).getResultList();
        });
    }
    public void save(String token, Long id, long revision, String username, String name, String email,
                     RoleCode role, boolean active, String initialHash) {
        auth.factory().inTransaction(s -> {
            AuthenticationDAO.role(s, RoleCode.ADMINISTRADOR, true);
            UserAccount actor = AuthenticationDAO.require(s, token, Permission.MANAGE_ACCOUNTS);
            UserAccount user = id == null ? new UserAccount() : s.find(UserAccount.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (user == null) throw new ValidationException("La cuenta ya no existe.");
            if (id != null && user.getRevision() != revision) throw new ValidationException("La cuenta cambió. Actualiza la tabla antes de editar.");
            RoleCode previousRole = id == null ? null : user.getRole().getCode();
            boolean previousActive = user.isActive();
            String previousName = user.getDisplayName(), previousEmail = user.getEmail();
            boolean changedAccess = id != null && (previousRole != role || previousActive != active);
            if (changedAccess && actor.getId().equals(id)) throw new ValidationException("No puedes cambiar tu propio rol o estado desde este panel.");
            if (changedAccess && previousRole == RoleCode.ADMINISTRADOR && previousActive && (role != RoleCode.ADMINISTRADOR || !active)) {
                long admins = s.createSelectionQuery("select count(a) from UserAccount a where a.active = true and a.role.code = :role", Long.class)
                        .setParameter("role", RoleCode.ADMINISTRADOR).getSingleResult();
                if (admins <= 1) throw new ValidationException("Debe permanecer al menos un administrador activo.");
            }
            if (id == null) {
                long duplicates = s.createSelectionQuery("select count(a) from UserAccount a where a.username = :name", Long.class)
                        .setParameter("name", username).getSingleResult();
                if (duplicates > 0) throw new ValidationException("Ese nombre de usuario ya está registrado.");
                user.setUsername(username); user.setPasswordHash(initialHash); user.setMustChangePassword(true);
            }
            user.setDisplayName(name); user.setEmail(email); user.setRole(AuthenticationDAO.role(s, role, false)); user.setActive(active);
            if (id == null) s.persist(user); else if (changedAccess) AuthenticationDAO.revokeAll(s, user);
            String detail = id == null ? "Cuenta creada con rol " + role + "; requiere cambio de contraseña."
                    : "Perfil: " + previousName + " → " + name + "; correo: " + java.util.Objects.toString(previousEmail, "—")
                    + " → " + java.util.Objects.toString(email, "—") + "; rol: " + previousRole + " → " + role + "; activa: " + previousActive + " → " + active
                    + (changedAccess ? ". Sesiones revocadas." : ".");
            AuthenticationDAO.audit(s, actor, user, "", id == null ? AuditAction.ACCOUNT_CREATED : AuditAction.ACCOUNT_UPDATED, detail);
        });
    }
    public void resetPassword(String token, long id, long revision, String hash) {
        auth.factory().inTransaction(s -> {
            UserAccount actor = AuthenticationDAO.require(s, token, Permission.MANAGE_ACCOUNTS);
            if (actor.getId().equals(id)) throw new ValidationException("Usa Cambiar mi contraseña para tu propia cuenta.");
            UserAccount user = s.find(UserAccount.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (user == null || user.getRevision() != revision) throw new ValidationException("La cuenta cambió. Actualiza la tabla.");
            user.setPasswordHash(hash); user.setMustChangePassword(true); user.setFailedAttempts(0); user.setLockedUntil(null);
            AuthenticationDAO.revokeAll(s, user);
            AuthenticationDAO.audit(s, actor, user, "", AuditAction.PASSWORD_RESET, "Contraseña temporal restablecida; debe cambiarse al iniciar. Sesiones revocadas.");
        });
    }
    public void unlock(String token, long id) {
        auth.factory().inTransaction(s -> {
            UserAccount actor = AuthenticationDAO.require(s, token, Permission.MANAGE_ACCOUNTS);
            UserAccount user = s.find(UserAccount.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (user == null) throw new ValidationException("La cuenta ya no existe.");
            user.setFailedAttempts(0); user.setLockedUntil(null);
            AuthenticationDAO.audit(s, actor, user, "", AuditAction.ACCOUNT_UNLOCKED, "Contador de intentos y bloqueo temporal restablecidos.");
        });
    }
    public List<AuditRow> audit(String token, Long target, AuditAction action, String search) {
        return auth.factory().fromTransaction(s -> {
            AuthenticationDAO.require(s, token, Permission.VIEW_AUDIT);
            return s.createSelectionQuery("""
                    select e.occurredAt, e.actorName, e.targetName, e.action, e.detail from AccountAudit e
                    where (:target is null or e.target.id = :target) and (:action is null or e.action = :action)
                    and (lower(e.actorName) like :search or lower(e.targetName) like :search)
                    order by e.occurredAt desc, e.id desc
                    """, AuditRow.class).setParameter("target", target).setParameter("action", action)
                    .setParameter("search", "%" + search.toLowerCase(java.util.Locale.ROOT) + "%").setMaxResults(200).getResultList();
        });
    }
}
