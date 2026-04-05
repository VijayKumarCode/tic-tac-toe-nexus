package com.vk.gaming.nexus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /*
     * Read allowed origin from environment variable so it works in all
     * environments without code changes. Falls back to local dev if not set.
     */
    @Value("${cors.allowed-origin:http://localhost:5500}")
    private String allowedOrigin;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // All endpoints are public — Nexus uses its own auth, not Spring Security
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "http://localhost:8080",
                "http://localhost:5500",
                "https://*.vercel.app",
                "https://*.up.railway.app",
                "https://nexusgame.space",
                "https://www.nexusgame.space",
                allowedOrigin
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);    // Required for SockJS / WebSocket

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * BUG FIX — SECURITY CRITICAL: Original code defined a hardcoded
     * InMemoryUserDetailsManager with username="dev" password="dev".
     *
     * This user existed in PRODUCTION (same SecurityConfig, no profile guard).
     * Anyone could potentially exploit this in edge-case Spring Security flows.
     *
     * Also, the Spring Security "generated password" warning was being suppressed
     * by the dummy user, hiding real security misconfigurations.
     *
     * Fix: Remove the dummy UserDetailsService entirely.
     * Nexus does NOT use Spring Security for authentication — it has its own
     * username/password/bcrypt flow in UserService. Spring Security is used
     * here ONLY for CORS configuration and to disable CSRF. No UserDetailsService
     * is needed at all. Spring will not auto-generate a password when no
     * UserDetailsService bean is present and security is fully permissive.
     *
     * If you later add JWT, define a real UserDetailsService at that time.
     */
}