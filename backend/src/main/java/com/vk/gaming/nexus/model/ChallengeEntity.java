package com.vk.gaming.nexus.model;

import com.vk.gaming.nexus.dto.ChallengeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "challenges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sender;
    private String receiver;
    private String roomId;

    @Enumerated(EnumType.STRING)
    private ChallengeStatus status;

    /*
     * BUG FIX: @CreationTimestamp was IMPORTED but never APPLIED to this field.
     * The import was present (misleadingly), but the annotation was missing.
     * Result: createdAt was always NULL when saved.
     *
     * Downstream effect: findTopByRoomIdOrderByCreatedAtDesc returned unpredictable
     * results when multiple challenges existed for the same room, because PostgreSQL
     * sorts NULLs first in DESC order — so "top by created_at desc" with all-null
     * values was effectively random.
     *
     * Fix: @PrePersist sets createdAt once on first save. Using @PrePersist instead
     * of @CreationTimestamp because:
     * - No extra Hibernate import needed
     * - Works identically with any JPA provider
     * - The intent is explicit and visible in the class
     */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}