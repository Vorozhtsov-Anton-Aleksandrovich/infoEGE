package com.github.anton.infoege.invite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Одноразовая ссылка. Хранится только SHA-256 токена.
 * userId пустой у приглашения нового ученика и заполнен у ссылки для сброса пароля.
 */
@Entity
@Table(name = "invite")
public class Invite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private String studentDisplayName;

    private Long userId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Invite() {
    }

    public Invite(String tokenHash, String studentDisplayName, Long userId, Instant expiresAt, Long createdBy) {
        this.tokenHash = tokenHash;
        this.studentDisplayName = studentDisplayName;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public boolean isExpired() {
        return !Instant.now().isBefore(expiresAt);
    }

    public boolean isPasswordReset() {
        return userId != null;
    }

    public Long getId() {
        return id;
    }

    public String getStudentDisplayName() {
        return studentDisplayName;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
