package com.vk.gaming.nexus.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "is_online", nullable = false)
    private Boolean isOnline = false; // Initialize to false by default

    @Column(name = "last_seen")
    private Long lastSeen;

    @Column(nullable = false)
    private Integer wins = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.OFFLINE;
    public enum UserStatus {
        ONLINE,
        IDLE,
        IN_GAME,
        OFFLINE
    }

}
