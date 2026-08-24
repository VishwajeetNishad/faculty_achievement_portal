package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A temporary, no-login address for one achievement.
 *
 * <p>A share link exists so that somebody with no account — an external
 * reviewer, a funding body, a collaborator at another institution — can read
 * one specific record. They open a URL containing a long random token and see
 * the achievement; there is no sign-up, no password and no invitation email.
 *
 * <p><strong>This makes the token a bearer credential.</strong> Anyone who
 * obtains it can use it, exactly as if it were a password for that one record,
 * until it expires or the owner revokes it. The design consequences:
 *
 * <ul>
 *   <li>the token is 32 bytes from {@code SecureRandom}, never derived from an
 *       id, an employee number or a timestamp, so it cannot be guessed or
 *       enumerated;</li>
 *   <li>{@link #expiresAt} is checked <em>by the server on every request</em>.
 *       The countdown a visitor sees in the browser is decoration;</li>
 *   <li>{@link #revoked} is a separate hard stop, so an owner can kill a link
 *       instantly without waiting for an expiry;</li>
 *   <li>the proof PDF is only reachable when {@link #includeProofDocument} was
 *       explicitly turned on — sharing a record is not the same as sharing the
 *       file behind it.</li>
 * </ul>
 *
 * <p>Only one link is active per achievement at a time: creating a new one
 * revokes the previous one, so an owner can never lose track of how many live
 * credentials point at their work.
 *
 * <p>Known trade-off, accepted deliberately: the token is stored in plain text
 * because the owner's "Copy link" button has to be able to show it again later.
 * Best practice for a credential would be to store only a hash and display the
 * value once. That would break the required feature, so the token is readable
 * to anyone with database access — which is the same trust level as reading the
 * research itself.
 */
@Entity
@Table(name = "share_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    /**
     * Who created the link. Always taken from the security context, never from
     * a request body, and always the owner of the achievement.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(name = "share_token", nullable = false, unique = true, length = 64)
    private String shareToken;

    /** {@code null} means the link never expires. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "include_proof_document", nullable = false)
    private boolean includeProofDocument;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * How many times the link has been opened, and when it was last opened, so
     * the owner can see whether the person they sent it to actually looked.
     * Deliberately not a per-visitor log: no IP addresses, no user agents,
     * nothing that would turn a share link into a tracking device.
     */
    @Column(name = "access_count", nullable = false)
    private long accessCount;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * True when the link has an expiry and that moment has passed.
     *
     * <p>Convenience only — the authoritative check happens in the service on
     * every public request. A helper here does not make the rule optional
     * anywhere else.
     */
    @Transient
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /** True when the link is neither revoked nor expired, so it still works. */
    @Transient
    public boolean isActive() {
        return !revoked && !isExpired();
    }
}
