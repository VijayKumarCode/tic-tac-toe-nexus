package com.vk.gaming.nexus.controller;

import com.vk.gaming.nexus.dto.AuthRequest;
import com.vk.gaming.nexus.dto.PlayerStatus;
import com.vk.gaming.nexus.entity.User;
import com.vk.gaming.nexus.repository.UserRepository;
import com.vk.gaming.nexus.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor // Automatically handles all 'private final' fields
@CrossOrigin(origins = "*")
@EnableScheduling
public class UserController {

    // FIXED: Removed @Autowired and made this final to enforce Constructor Injection
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        try {
            User user = userService.registerUser(request);
            return ResponseEntity.ok(user);
        }
        catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            User user = userService.loginUser(request);
            // FIXED: Broadcast the ONLINE status immediately upon login
            messagingTemplate.convertAndSend("/topic/lobby.status",
                    new PlayerStatus(user.getUsername(), "ONLINE"));
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/logout/{username}")
    public ResponseEntity<?> logout(@PathVariable String username) {
        userService.logoutUser(username);
        // FIXED: Broadcast the OFFLINE status to immediately remove them from other screens
        messagingTemplate.convertAndSend("/topic/lobby.status",
                new PlayerStatus(username, "OFFLINE"));
        return ResponseEntity.ok(username + " logged out successfully");
    }


    @RequestMapping(value = "/sync", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> syncPresence(@RequestParam String username) {
        userRepository.findByUsername(username).ifPresent(u -> {
            u.setStatus(User.UserStatus.ONLINE);
            u.setIsOnline(true);
            userRepository.save(u);
            messagingTemplate.convertAndSend("/topic/lobby.status", new PlayerStatus(username, "ONLINE"));
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/heartbeat")
    public void heartbeat(@RequestParam String username) {
        userService.heartbeat(username);
    }

    @GetMapping("/lobby")
    public List<User> getLobby() {
        return userService.getAllUsers();
    }

    @Scheduled(fixedRate = 10000)
    @Transactional
    public void updateIdleUsers() {
        long now = System.currentTimeMillis();

        userRepository.findAll().forEach(user -> {
            // FIXED 1: Skip users who are already OFFLINE to prevent "Offline Resurrection"
            if (user.getStatus() == User.UserStatus.OFFLINE) return;

            long lastSeen = user.getLastSeen() != null ? user.getLastSeen() : 0;
            long timeSinceLastSeen = now - lastSeen;

            // FIXED 2: Ghost Session Cleanup (User closed tab while IN_GAME)
            // If no heartbeat for > 2 minutes, forcefully log them out of PostgreSQL
            if (timeSinceLastSeen > 120000) {
                user.setStatus(User.UserStatus.OFFLINE);
                user.setIsOnline(false);
                userRepository.save(user);
                return;
            }

            // If user is actively IN_GAME and hasn't timed out, leave them alone
            if (user.getStatus() == User.UserStatus.IN_GAME) return;

            // Standard IDLE logic: Set to IDLE after 60 seconds of no heartbeat
            if (timeSinceLastSeen > 60000 && user.getStatus() != User.UserStatus.IDLE) {
                user.setStatus(User.UserStatus.IDLE);
                userRepository.save(user); // FIXED: Added explicit save for safety
            }
        });
    }

    @GetMapping("/leaderboard")
    public List<User> getLeaderboard() {
        return userRepository.findTop10ByOrderByWinsDesc();
    }
}
