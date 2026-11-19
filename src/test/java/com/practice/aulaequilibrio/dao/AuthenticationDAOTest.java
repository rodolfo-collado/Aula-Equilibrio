package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.model.AccountData.*;
import com.practice.aulaequilibrio.service.*;
import com.practice.aulaequilibrio.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationDAOTest {
    private static final String PASSWORD = "Test-account-password-42";
    private SessionFactory factory;
    private AuthenticationDAO dao;
    private AuthenticationService auth;
    private Identity admin;
    private AccountService accounts;
    @BeforeEach void setup() {
        factory = DashboardDAOTest.createFactory(); dao = new AuthenticationDAO(factory); auth = new AuthenticationService(dao); auth.prepareRoles();
        admin = auth.createInitialAdmin("admin", "Administración", null, PASSWORD.toCharArray());
        accounts = service(admin);
    }
    @AfterEach void close() { SessionContext.clear(); factory.close(); }
    private AccountService service(Identity identity) { return new AccountService(new AccountDAO(dao), new AccessControl(identity, dao)); }
    private AccountRow account(String username) { return accounts.list().stream().filter(a -> a.username().equals(username)).findFirst().orElseThrow(); }
    private void create(String username, RoleCode role) { accounts.save(null, 0, username, "Persona " + username, null, role, true, PASSWORD.toCharArray()); }
    private Identity ready(String username) {
        Identity initial = auth.login(username, PASSWORD.toCharArray());
        return auth.changePassword(initial.token(), PASSWORD.toCharArray(), (PASSWORD + "-new").toCharArray());
    }

    @Test void bootstrapIsOneTimePasswordsAreSaltedAndWrongCredentialsAreAudited() {
        assertFalse(auth.needsSetup());
        assertThrows(ValidationException.class, () -> auth.createInitialAdmin("another", "Otra cuenta", null, PASSWORD.toCharArray()));
        create("reader", RoleCode.CONSULTA);
        var hashes = factory.fromTransaction(s -> s.createNativeQuery("select password_hash from user_account order by id", String.class).getResultList());
        assertEquals(2, hashes.size()); assertNotEquals(hashes.getFirst(), hashes.getLast());
        assertTrue(hashes.stream().allMatch(hash -> hash.startsWith("pbkdf2-sha256$") && !hash.contains(PASSWORD)));
        assertThrows(ValidationException.class, () -> auth.login("reader", "incorrect-password".toCharArray()));
        assertEquals(1, accounts.audit(null, AuditAction.LOGIN_FAILED, "reader").size());
        assertTrue(accounts.audit(null, null, "reader").stream().noneMatch(a -> a.detail().contains(PASSWORD) || a.detail().contains("pbkdf2")));
    }

    @Test void temporaryPasswordCannotAccessTheApplicationUntilChangedAndOldSessionsAreRevoked() {
        create("reader", RoleCode.CONSULTA);
        Identity temporary = auth.login("reader", PASSWORD.toCharArray());
        assertTrue(temporary.mustChangePassword());
        assertThrows(AuthorizationException.class, () -> dao.requireAccess(temporary.token(), Permission.READ_ACADEMIC));
        Identity active = auth.changePassword(temporary.token(), PASSWORD.toCharArray(), (PASSWORD + "-new").toCharArray());
        dao.requireAccess(active.token(), Permission.READ_ACADEMIC);
        assertThrows(AuthorizationException.class, () -> dao.requireAccess(temporary.token(), Permission.READ_ACADEMIC));
        assertThrows(ValidationException.class, () -> auth.login("reader", PASSWORD.toCharArray()));
        assertFalse(auth.login("reader", (PASSWORD + "-new").toCharArray()).mustChangePassword());
    }

    @Test void eachRoleHasItsOwnCapabilitiesAndServicesRejectUnauthorizedWrites() {
        for (RoleCode code : new RoleCode[]{RoleCode.COORDINADOR, RoleCode.PLANIFICADOR, RoleCode.CONSULTA, RoleCode.AUDITOR}) {
            String username = code.name().toLowerCase(); create(username, code); Identity user = ready(username);
            AccessControl gate = new AccessControl(user, dao);
            gate.require(Permission.READ_ACADEMIC);
            for (Permission permission : Permission.values()) {
                if (code.allows(permission)) assertDoesNotThrow(() -> gate.require(permission));
                else assertThrows(AuthorizationException.class, () -> gate.require(permission));
            }
            if (code != RoleCode.COORDINADOR) {
                assertThrows(AuthorizationException.class, () -> new CatalogService(new CatalogDAO(factory), gate).saveTeacher(null, "Sin permiso", null, true));
                assertThrows(AuthorizationException.class, () -> new AcademicOfferService(new AcademicOfferDAO(), gate).deleteSubject(1));
                assertThrows(AuthorizationException.class, () -> new AcademicStructureService(new AcademicStructureDAO(), gate).deleteFaculty(1));
                assertThrows(AuthorizationException.class, () -> new ClassroomService(new ClassroomDAO(), new ClassroomTypeDAO(), gate).deleteType(null));
            }
            if (code == RoleCode.CONSULTA || code == RoleCode.AUDITOR)
                assertThrows(AuthorizationException.class, () -> new ScheduleService(new ScheduleDAO(factory), gate).saveVersion(null, "No autorizado"));
            assertThrows(AuthorizationException.class, () -> service(user).list());
        }
    }

    @Test void changesToRolesStatusAndPasswordInvalidateExistingSessions() {
        create("worker", RoleCode.COORDINADOR); Identity worker = ready("worker");
        AccountRow before = account("worker");
        accounts.save(before.id(), before.revision(), before.username(), "Perfil editado", null, RoleCode.CONSULTA, true, null);
        assertThrows(AuthorizationException.class, () -> dao.requireAccess(worker.token(), Permission.READ_ACADEMIC));
        Identity reader = auth.login("worker", (PASSWORD + "-new").toCharArray());
        assertEquals(RoleCode.CONSULTA, reader.role());
        accounts.resetPassword(account("worker"), (PASSWORD + "-reset").toCharArray());
        assertThrows(AuthorizationException.class, () -> dao.requireAccess(reader.token(), Permission.READ_ACADEMIC));
        Identity reset = auth.login("worker", (PASSWORD + "-reset").toCharArray()); assertTrue(reset.mustChangePassword());
        AccountRow current = account("worker");
        accounts.save(current.id(), current.revision(), current.username(), current.displayName(), null, current.role(), false, null);
        assertThrows(ValidationException.class, () -> auth.login("worker", (PASSWORD + "-reset").toCharArray()));
        assertThrows(AuthorizationException.class, () -> dao.requireAccess(reset.token(), Permission.READ_ACADEMIC));
        assertFalse(accounts.audit(current.id(), AuditAction.ACCOUNT_UPDATED, "").isEmpty());
    }

    @Test void sessionExpiryLogoutLockoutAndUnlockAreEnforced() {
        create("reader", RoleCode.CONSULTA);
        for (int i = 0; i < 5; i++) assertThrows(ValidationException.class, () -> auth.login("reader", "incorrect-password".toCharArray()));
        assertThrows(ValidationException.class, () -> auth.login("reader", PASSWORD.toCharArray()));
        assertNotNull(account("reader").lockedUntil()); accounts.unlock(account("reader"));
        Identity user = ready("reader");
        auth.logout(user.token());
        assertThrows(AuthorizationException.class, () -> dao.requireAccess(user.token(), Permission.READ_ACADEMIC));
        Identity another = auth.login("reader", (PASSWORD + "-new").toCharArray());
        factory.inTransaction(s -> s.createMutationQuery("update LoginSession set expiresAt = :past where tokenHash = :hash")
                .setParameter("past", Instant.now().minusSeconds(1)).setParameter("hash", PasswordHasher.tokenHash(another.token())).executeUpdate());
        assertThrows(AuthorizationException.class, () -> dao.requireAccess(another.token(), Permission.READ_ACADEMIC));
    }

    @Test void adminCannotDemoteOrDeactivateItselfAndConcurrentBootstrapCreatesOnlyOneAccount() throws Exception {
        AccountRow own = account("admin");
        assertThrows(ValidationException.class, () -> accounts.save(own.id(), own.revision(), own.username(), own.displayName(), null, RoleCode.CONSULTA, false, null));
        assertEquals(RoleCode.ADMINISTRADOR, account("admin").role());
        try (var other = DashboardDAOTest.createFactory(); var workers = Executors.newFixedThreadPool(2)) {
            AuthenticationDAO otherDao = new AuthenticationDAO(other); AuthenticationService setup = new AuthenticationService(otherDao); setup.prepareRoles();
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> create = () -> { start.await(); try { setup.createInitialAdmin("first-admin", "Primero", null, PASSWORD.toCharArray()); return true; } catch (ValidationException error) { return false; } };
            var first = workers.submit(create); var second = workers.submit(create); start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            long accountCount = other.fromTransaction(s -> s.createSelectionQuery("select count(a) from UserAccount a", Long.class).getSingleResult());
            assertEquals(1L, accountCount);
        }
    }
}
