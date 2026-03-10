package com.vk.gaming.nexus.controller;

import com.vk.gaming.nexus.entity.User;
import com.vk.gaming.nexus.servce.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*") // Industry standard to allow frontend to talk to backend
public class UserController {

    @Autowired
    private UserService userService;

    // Registers the user in PostgreSQL
    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestParam String username, @RequestParam String password) {
        User newUser = userService.registerUser(username,password);
        return ResponseEntity.ok(newUser);
    }

    // Logs the user in and flips is_online to TRUE
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam String username, @RequestParam String password) {
        // Note: For a production app, we will add password verification here later!
        userService.setUserOnlineStatus(username,true);
        return ResponseEntity.ok(username + " successfully logged in and is now online.");
    }

    // Logs the user out and flips is_online to FALSE
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestParam String username, @RequestParam String password) {
        userService.setUserOnlineStatus(username,false);
        return  ResponseEntity.ok(username + "logged out" );
    }

    // The endpoint your LobbyPanel will call to get active players
    @GetMapping("/online")
    public ResponseEntity<List<User>> getOnlinePlayers() {
        return ResponseEntity.ok(userService.getOnlinePlayers());
    }
}
