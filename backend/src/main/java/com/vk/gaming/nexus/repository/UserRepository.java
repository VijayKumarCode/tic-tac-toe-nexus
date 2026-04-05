package com.vk.gaming.nexus.repository;

import com.vk.gaming.nexus.entity.User;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;   // Spring, not jakarta

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByActivationToken(String token);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByStatus(User.UserStatus status);

    @Query("SELECT u FROM User u WHERE (u.wins + u.losses) > 0 ORDER BY u.wins DESC LIMIT 10")
    List<User> findTop10ActivePlayers();

    /*
     * BUG FIX — CRITICAL: Original JPQL used `User$UserStatus.OFFLINE`
     * The `$` is the JVM bytecode separator for inner classes.
     * JPQL uses the Java source separator `.` — but both can fail depending
     * on the Hibernate version. The safest approach is to pass the enum
     * value as a named parameter, which is guaranteed to work everywhere.
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE User u
        SET u.status = :offline
        WHERE u.lastSeen < :cutoff
        AND u.status <> :offline
    """)
    int markInactiveUsersOffline(
            @Param("cutoff") long cutoff,
            @Param("offline") User.UserStatus offline
    );

    /*
     * BUG FIX: updateHeartbeat JPQL used bare string 'IN_GAME' and 'ONLINE'.
     * That works only because the enum is stored as STRING, but it silently
     * breaks if the enum name changes. Use enum parameters instead.
     *
     * Also fixed: 'IN_GAME' string literal in CASE WHEN should compare
     * against the enum value. Using parameter is cleaner and type-safe.
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE User u
        SET u.lastSeen = :time,
            u.status   = CASE
                WHEN u.status <> :inGame THEN :online
                ELSE u.status
            END
        WHERE u.username = :username
    """)
    int updateHeartbeat(
            @Param("username") String username,
            @Param("time")     long time,
            @Param("inGame")   User.UserStatus inGame,
            @Param("online")   User.UserStatus online
    );

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.wins = u.wins + 1 WHERE u.username = :username")
    int incrementWins(@Param("username") String username);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.losses = u.losses + 1 WHERE u.username = :username")
    int incrementLosses(@Param("username") String username);
}