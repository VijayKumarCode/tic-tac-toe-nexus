/**
 * Problem No. #152
 * Difficulty: Medium
 * Description: Standardized presence logic and constructor injection in UserService
 * Link: https://github.com/VijayKumarCode/Nexus
 * Time Complexity: O(1)
 * Space Complexity: O(1)
 */
package com.vk.gaming.nexus.service;

import com.vk.gaming.nexus.dto.AuthRequest;
import com.vk.gaming.nexus.entity.User;
import com.vk.gaming.nexus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor // FIXED: Use Lombok to match your Controller's style
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User registerUser(AuthRequest request) {
        Optional<User> existingUser = userRepository.findByUsername(request.getUsername());
        if (existingUser.isPresent()) {
            throw new RuntimeException("Username already exists!");
        }

        User newUser = new User();
        newUser.setFullName(request.getFullName());
        newUser.setUsername(request.getUsername());
        newUser.setPassword(request.getPassword());
        newUser.setStatus(User.UserStatus.OFFLINE);
        newUser.setIsOnline(false); // Ensure consistency
        return userRepository.save(newUser);
    }

    @Transactional
    public User loginUser(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found!"));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("Invalid credentials!");
        }

        // FIXED: Sync both fields during login
        user.setStatus(User.UserStatus.ONLINE);
        user.setIsOnline(true);
        user.setLastSeen(System.currentTimeMillis());
        return userRepository.save(user);
    }

    @Transactional
    public void logoutUser(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setStatus(User.UserStatus.OFFLINE);
            user.setIsOnline(false); // FIXED: Sync both fields during logout
            userRepository.save(user);
        });
    }

    public void heartbeat(String username) {
        userRepository.findByUsername(username).ifPresent(u -> {
            u.setLastSeen(System.currentTimeMillis());

            if (u.getStatus() != User.UserStatus.IN_GAME) {
                u.setStatus(User.UserStatus.ONLINE);
            }

            userRepository.save(u);
        });
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

}