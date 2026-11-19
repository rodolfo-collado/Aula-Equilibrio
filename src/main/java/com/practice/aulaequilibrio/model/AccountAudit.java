package com.practice.aulaequilibrio.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "account_audit", indexes = @Index(name = "ix_account_audit_target", columnList = "target_id,occurred_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt = Instant.now();
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id") private UserAccount actor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "target_id") private UserAccount target;
    @Column(name = "actor_name", nullable = false, length = 40) private String actorName;
    @Column(name = "target_name", nullable = false, length = 40) private String targetName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AuditAction action;
    @Column(nullable = false, length = 1000) private String detail;
    public AccountAudit(UserAccount actor, UserAccount target, String claimedName, AuditAction action, String detail) {
        this.actor = actor; this.target = target;
        this.actorName = actor == null ? "Sin autenticar" : actor.getUsername();
        this.targetName = target == null ? claimedName : target.getUsername();
        this.action = action; this.detail = detail;
    }
}
