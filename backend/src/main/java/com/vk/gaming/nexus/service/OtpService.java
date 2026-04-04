/**
 * Problem No. #197
 * Difficulty: Medium
 * Description: OtpService using Resend HTTP API (not SMTP).
 * Reason: Railway blocks outbound port 587. SMTP calls hang for 2+ minutes
 *         then silently fail. Resend HTTP API uses port 443 (HTTPS) which
 *         is always open, gives real error messages, and is 10x faster.
 * Link: https://github.com/VijayKumarCode/Nexus
 * Time Complexity: O(1) for generation/verification, O(n) for periodic cleanup
 * Space Complexity: O(u) where u is active unverified users
 */
package com.vk.gaming.nexus.service;

import com.vk.gaming.nexus.config.AppConfig;
import com.vk.gaming.nexus.dto.OtpData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final AppConfig appConfig;

    /* ── Resend API key injected from env var RESEND_API_KEY ── */
    @Value("${RESEND_API_KEY}")
    private String resendApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final Map<String, OtpData> otpStorage = new ConcurrentHashMap<>();
    private static final long OTP_EXPIRY = 10 * 60 * 1000; // 10 minutes
    private static final SecureRandom secureRandom = new SecureRandom();

    /* ══════════════════════════════════
       CORE HTTP SEND — replaces mailSender.send()
       Uses Resend REST API on port 443 (HTTPS).
       Railway never blocks port 443.
    ══════════════════════════════════ */
    private void sendEmail(String to, String subject, String textBody) {
        log.info(">>> Sending email via Resend HTTP API to={} subject={}", to, subject);
        log.info(">>> FROM address = {}", appConfig.getMailFrom());
        log.info(">>> RESEND_API_KEY present = {}", (resendApiKey != null && !resendApiKey.isBlank()));

        /* Build JSON body manually — no extra dependencies needed */
        String htmlBody = textBody.replace("\n", "<br>");
        String jsonBody = """
                {
                  "from": "%s",
                  "to": ["%s"],
                  "subject": "%s",
                  "text": "%s",
                  "html": "<p>%s</p>"
                }
                """.formatted(
                appConfig.getMailFrom(),
                to,
                subject,
                textBody.replace("\"", "\\\"").replace("\n", "\\n"),
                htmlBody
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(resendApiKey);

        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    RESEND_API_URL, request, String.class
            );
            log.info("✅ Email sent via Resend HTTP API. Status={} Body={}",
                    response.getStatusCode(), response.getBody());
        } catch (HttpClientErrorException e) {
            log.error("❌ Resend API rejected the request. Status={} Body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Email delivery failed: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("❌ Resend API call failed: {}", e.getMessage());
            throw new RuntimeException("Email delivery failed: " + e.getMessage());
        }
    }

    /* ══════════════════════════════════
       OTP GENERATION
    ══════════════════════════════════ */
    public String generateOtp(String email) {
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        otpStorage.put(email, new OtpData(otp, System.currentTimeMillis()));
        return otp;
    }

    public void sendOtp(String email, String otp) {
        String subject = "Nexus Multiplayer — Verification Code";
        String body    = "Your OTP is: " + otp + "\n\nValid for 10 minutes.\n\nDo not share this code.";
        sendEmail(email, subject, body);
        log.info("OTP sent to {}", email);
    }

    public boolean verifyOtp(String email, String inputOtp) {
        OtpData data = otpStorage.get(email);

        if (data == null) {
            return false;
        }

        if (System.currentTimeMillis() - data.getCreatedAt() > OTP_EXPIRY) {
            otpStorage.remove(email);
            log.warn("OTP expired for {}", email);
            return false;
        }

        if (!data.getOtp().equals(inputOtp)) {
            return false;
        }

        otpStorage.remove(email);
        return true;
    }

    public void generateAndSendOtp(String email) {
        String otp = generateOtp(email);
        sendOtp(email, otp);
        log.info("OTP generated + sent for {}", email);
    }

    /* ══════════════════════════════════
       ACTIVATION LINK
       NOTE: Exception is now THROWN, not swallowed.
       If email fails, register() returns an error to the user
       instead of silently succeeding.
    ══════════════════════════════════ */
    public void sendActivationLink(String email, String token) {
        log.info(">>> BASE URL FROM CONFIG = {}", appConfig.getBaseUrl());
        log.info(">>> MAIL FROM = {}", appConfig.getMailFrom());

        String activationUrl = appConfig.getBaseUrl()
                + "/api/users/activate?token=" + token;

        String subject = "Activate Your Nexus Account";
        String body    = "Welcome to Nexus Multiplayer!\n\n"
                + "Click the link below to activate your account:\n"
                + activationUrl + "\n\n"
                + "This link expires in 24 hours.\n\n"
                + "If you did not register, ignore this email.";

        /* Exception propagates up — register() will catch it and return a proper error */
        sendEmail(email, subject, body);
        log.info("✅ Activation email dispatched via Resend HTTP API to {}", email);
    }

    /* ══════════════════════════════════
       MEMORY CLEANUP
    ══════════════════════════════════ */
    @Scheduled(fixedRate = 600000)
    public void cleanExpiredOtps() {
        long now         = System.currentTimeMillis();
        int  initialSize = otpStorage.size();
        otpStorage.entrySet().removeIf(e -> now - e.getValue().getCreatedAt() > OTP_EXPIRY);
        int removed = initialSize - otpStorage.size();
        if (removed > 0) {
            log.info("Cleaned up {} expired OTPs from memory.", removed);
        }
    }
}