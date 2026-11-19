package com.practice.aulaequilibrio.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "login_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id", nullable = false)
    private UserAccount account;
    @Column(name = "security_version", nullable = false) private long securityVersion;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") @Setter private Instant revokedAt;
    public LoginSession(String tokenHash, UserAccount account, Instant expiresAt) {
        this.tokenHash = tokenHash; this.account = account; this.expiresAt = expiresAt;
        this.securityVersion = account.getSecurityVersion();
    }
}
