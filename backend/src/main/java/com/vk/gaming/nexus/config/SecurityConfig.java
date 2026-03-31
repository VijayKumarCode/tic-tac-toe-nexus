/*package com.vk.gaming.nexus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 🔥 Disable CSRF for REST APIs (OK for now)
                .csrf(AbstractHttpConfigurer::disable)

                // 🔥 Stateless session (important for future JWT)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 🔥 Route protection
                .authorizeHttpRequests(auth -> auth

                        // ✅ PUBLIC ENDPOINTS (whitelisted)
                        .requestMatchers(
                                "/api/users/register",
                                "/api/users/login",
                                "/api/users/activate",
                                "/api/recovery/**",
                                "/game-websocket/**"
                        ).permitAll()

                        // 🔒 EVERYTHING ELSE PROTECTED
                        .anyRequest().authenticated()
                )

                // 🔥 Disable default login UI
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}*/

/**
 * Problem No. #190
 * Difficulty: Medium
 * Description: Fixed Global CORS policy and silenced default security password logs.
 * Link: https://github.com/VijayKumarCode/Nexus
 * Time Complexity: O(1)
 * Space Complexity: O(1)
 */
package com.vk.gaming.nexus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Enable our custom CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 2. Disable CSRF (required for testing POST requests from frontend)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    // 🔥 FIX 2: Explicitly define what traffic is allowed (Crucial for WebSockets)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allows requests from any origin (your frontend)
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:8080",
                "http://localhost:5500",
                "https://*.vercel.app",
                "https://*.up.railway.app",
                "https://nexusgame.space",      // Add this
                "https://www.nexusgame.space"   // Add this
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Required for WebSockets/SockJS to work
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 🔥 FIX 3: A dummy user just to stop Spring from generating a random password in the logs
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
                User.withUsername("dev")
                        .password("{noop}dev") // {noop} means plain text for dev only
                        .roles("USER")
                        .build()
        );
    }
}