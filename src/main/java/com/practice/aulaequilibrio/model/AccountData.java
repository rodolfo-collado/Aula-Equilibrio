package com.practice.aulaequilibrio.model;

import java.time.Instant;

public final class AccountData {
    private AccountData() {}
    public record Identity(String token, String username, String displayName, RoleCode role, boolean mustChangePassword) {
        @Override public String toString() { return username + " · " + role; }
    }
    public record AccountRow(Long id, long revision, String username, String displayName, String email, RoleCode role,
                             boolean active, boolean mustChangePassword, Instant lockedUntil, Instant lastLogin) {}
    public record AuditRow(Instant occurredAt, String actorName, String targetName, AuditAction action, String detail) {}
}
