package com.vk.gaming.nexus.repository;

import com.vk.gaming.nexus.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    // Used to check if a user exists during login
    Optional<User> findByUsername(String username);

    // Used by the LobbyPanel to fetch ONLY players who are currently online
    List<User> findByIsOnlineTrue();
}
