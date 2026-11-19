package com.practice.aulaequilibrio.model;

import com.practice.aulaequilibrio.util.PasswordHasher;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "user_account")
@Getter
@NoArgsConstructor
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private long revision;
    @Column(nullable = false, unique = true, length = 40, updatable = false) @Setter
    private String username;
    @Column(name = "display_name", nullable = false, length = 120) @Setter
    private String displayName;
    @Column(length = 120) @Setter private String email;
    @Getter(AccessLevel.NONE) @Setter @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false) @Setter
    private UserRole role;
    @Column(nullable = false) @Setter private boolean active = true;
    @Column(name = "must_change_password", nullable = false) @Setter private boolean mustChangePassword;
    @Column(name = "security_version", nullable = false) @Setter private long securityVersion;
    @Column(name = "failed_attempts", nullable = false) @Setter private int failedAttempts;
    @Column(name = "locked_until") @Setter private Instant lockedUntil;
    @Column(name = "last_login") @Setter private Instant lastLogin;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    public boolean matchesPassword(char[] password) { return PasswordHasher.verify(password, passwordHash); }
    @Override public String toString() { return username; }
}
