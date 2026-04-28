package com.vk.gaming.nexus.dto;

import lombok.Data;

@Data
public class AuthRequest {
        private String fullName;
        private String username;
        private String email;
        private String password;
    }
