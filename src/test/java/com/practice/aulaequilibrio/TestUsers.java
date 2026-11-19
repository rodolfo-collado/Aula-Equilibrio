package com.practice.aulaequilibrio;

import com.practice.aulaequilibrio.dao.AuthenticationDAO;
import com.practice.aulaequilibrio.service.*;
import com.practice.aulaequilibrio.util.SessionContext;
import org.hibernate.SessionFactory;

public final class TestUsers {
    private TestUsers() {}
    public static AccessControl admin(SessionFactory factory) {
        AuthenticationDAO dao = new AuthenticationDAO(factory);
        AuthenticationService service = new AuthenticationService(dao); service.prepareRoles();
        var identity = service.needsSetup()
                ? service.createInitialAdmin("test-admin", "Administrador de pruebas", null, "TestOnly-Password-42".toCharArray())
                : service.login("test-admin", "TestOnly-Password-42".toCharArray());
        SessionContext.install(identity);
        return new AccessControl(identity, dao);
    }
}
