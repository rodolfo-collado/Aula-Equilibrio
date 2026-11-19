package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.AuthenticationDAO;
import com.practice.aulaequilibrio.model.AccountData.Identity;
import com.practice.aulaequilibrio.model.Permission;
import com.practice.aulaequilibrio.util.SessionContext;

/** Cada servicio conserva la sesión que lo abrió; no adopta la de otro inicio de sesión. */
public final class AccessControl {
    private final Identity identity;
    private final AuthenticationDAO auth;
    public AccessControl(Identity identity, AuthenticationDAO auth) { this.identity = identity; this.auth = auth; }
    public static AccessControl current() { return new AccessControl(SessionContext.current(), new AuthenticationDAO()); }
    public boolean can(Permission permission) { return identity != null && !identity.mustChangePassword() && identity.role().allows(permission); }
    public void require(Permission permission) {
        if (identity == null) throw new com.practice.aulaequilibrio.util.AuthorizationException("Inicia sesión para continuar.");
        auth.requireAccess(token(), permission);
    }
    public String token() { return identity == null ? null : identity.token(); }
    public Identity identity() { return identity; }
}
