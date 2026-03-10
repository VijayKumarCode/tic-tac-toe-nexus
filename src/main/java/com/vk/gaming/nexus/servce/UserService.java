package com.vk.gaming.nexus.servce;

import com.vk.gaming.nexus.entity.User;
import com.vk.gaming.nexus.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // Registers a new user and saves them to PostgreSQL
    public User registerUser( String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password); //Note: In production, we will encrypt this.
        user.setOnline(false);
        return userRepository.save(user);
    }

    public void setUserOnlineStatus(String username, boolean status) {

        // This fixes the "everyone is online" bug by explicitly toggling status
        userRepository.findByUsername(username).ifPresent(user -> {
                user.setOnline(status);
                userRepository.save(user);
        });
    }

    // Used by LobbyPanel to fetch only active players
    public List<User> getOnlinePlayers() {
        return userRepository.findByIsOnlineTrue();
    }

}
