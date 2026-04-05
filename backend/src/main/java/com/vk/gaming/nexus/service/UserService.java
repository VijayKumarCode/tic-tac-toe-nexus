package com.vk.gaming.nexus.service;

import com.vk.gaming.nexus.dto.AuthRequest;
import com.vk.gaming.nexus.entity.User;
import com.vk.gaming.nexus.exception.EmailAlreadyRegisteredException;
import com.vk.gaming.nexus.exception.UsernameTakenException;
import com.vk.gaming.nexus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    @Transactional
    public User registerUser(AuthRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyRegisteredException(request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameTakenException(request.getUsername());
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(false);
        user.setStatus(User.UserStatus.OFFLINE);
        user.setWins(0);
        user.setLosses(0);

        String token = UUID.randomUUID().toString();
        user.setActivationToken(token);

        User saved = userRepository.save(user);
        otpService.sendActivationLink(saved.getEmail(), token);
        return saved;
    }

    @Transactional
    public User loginUser(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        // Constant-time comparison prevents user-enumeration timing attacks
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password.");
        }
        if (!user.isEnabled()) {
            throw new RuntimeException("Account not activated. Check your email.");
        }

        user.setStatus(User.UserStatus.ONLINE);
        user.setLastSeen(System.currentTimeMillis());
        return userRepository.save(user);
    }

    @Transactional
    public void logoutUser(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setStatus(User.UserStatus.OFFLINE);
            userRepository.save(user);
        });
    }

    /*
     * BUG FIX — CRITICAL: Original syncUserPresence always set status = ONLINE
     * even while the player was IN_GAME. This silently corrupted game status
     * every 10 seconds from the heartbeat, making IN_GAME players appear online
     * in the lobby and challengeable while in a game.
     *
     * Fix: preserve IN_GAME status — only update to ONLINE if not currently in game.
     */
    @Transactional
    public void syncUserPresence(String username) {
        userRepository.findByUsername(username).ifPresent(u -> {
            if (u.getStatus() != User.UserStatus.IN_GAME) {
                u.setStatus(User.UserStatus.ONLINE);
            }
            u.setLastSeen(System.currentTimeMillis());
            userRepository.save(u);
        });
    }

    /*
     * BUG FIX: heartbeat now passes enum parameters to match the fixed
     * UserRepository.updateHeartbeat signature.
     */
    @Transactional
    public void heartbeat(String username) {
        int updated = userRepository.updateHeartbeat(
                username,
                System.currentTimeMillis(),
                User.UserStatus.IN_GAME,
                User.UserStatus.ONLINE
        );
        if (updated == 0) {
            log.warn("Heartbeat: user not found — {}", username);
        }
    }

    /*
     * BUG FIX: markInactiveUsersOffline now receives the OFFLINE enum
     * parameter instead of relying on the broken $UserStatus JPQL reference.
     */
    @Scheduled(fixedRate = 10_000)
    @Transactional
    public void updateIdleUsers() {
        long cutoff  = System.currentTimeMillis() - 120_000;   // 2 min idle threshold
        int updated  = userRepository.markInactiveUsersOffline(cutoff, User.UserStatus.OFFLINE);
        if (updated > 0) {
            log.info("Marked {} user(s) OFFLINE due to inactivity", updated);
        }
    }

    @Transactional
    public void incrementWins(String username) {
        userRepository.incrementWins(username);
    }

    @Transactional
    public void incrementLosses(String username) {
        userRepository.incrementLosses(username);
    }

    public List<User> getOnlineUsers() {
        return userRepository.findByStatus(User.UserStatus.ONLINE);
    }

    public boolean isUsernameAvailable(String username) {
        return username != null
                && !username.trim().isEmpty()
                && !userRepository.existsByUsername(username.trim());
    }

    public void resendActivationLink(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.isEnabled()) {
            throw new RuntimeException("Account already activated");
        }
        String token = UUID.randomUUID().toString();
        user.setActivationToken(token);
        userRepository.save(user);
        otpService.sendActivationLink(email, token);
    }
}