package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.AccountDAO;
import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.model.AccountData.*;
import com.practice.aulaequilibrio.util.*;
import lombok.RequiredArgsConstructor;
import java.util.List;

@RequiredArgsConstructor
public class AccountService {
    private final AccountDAO dao;
    private final AccessControl access;
    public AccountService() { this(new AccountDAO(), AccessControl.current()); }
    public boolean canManage() { return access.can(Permission.MANAGE_ACCOUNTS); }
    public List<AccountRow> list() { return dao.list(access.token()); }
    public void save(Long id, long revision, String username, String name, String email, RoleCode role, boolean active, char[] password) {
        try {
            access.require(Permission.MANAGE_ACCOUNTS);
            if (role == null) throw new ValidationException("Selecciona un rol.");
            String hash = id == null ? PasswordHasher.hash(password) : null;
            dao.save(access.token(), id, revision, id == null ? AuthenticationService.username(username) : username,
                    Validations.required(name, "nombre", 120), AuthenticationService.email(email), role, active, hash);
        } finally { AuthenticationService.clear(password); }
    }
    public void resetPassword(AccountRow account, char[] password) {
        try {
            access.require(Permission.MANAGE_ACCOUNTS);
            dao.resetPassword(access.token(), account.id(), account.revision(), PasswordHasher.hash(password));
        } finally { AuthenticationService.clear(password); }
    }
    public void unlock(AccountRow account) { dao.unlock(access.token(), account.id()); }
    public List<AuditRow> audit(Long target, AuditAction action, String query) {
        return dao.audit(access.token(), target, action, query == null ? "" : query.strip());
    }
}
