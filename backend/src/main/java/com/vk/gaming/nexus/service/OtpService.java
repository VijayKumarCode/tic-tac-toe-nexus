package com.vk.gaming.nexus.service;

import com.vk.gaming.nexus.config.AppConfig;
import com.vk.gaming.nexus.dto.OtpData;
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
    private final RestTemplate restTemplate;   // BUG FIX: injected as Spring bean, not new RestTemplate()

    @Value("${RESEND_API_KEY}")
    private String resendApiKey;

    private static final String RESEND_URL   = "https://api.resend.com/emails";
    private static final long   OTP_EXPIRY   = 10 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, OtpData> otpStorage = new ConcurrentHashMap<>();

    /* ══════════════════════════════════
       CORE HTTP EMAIL SEND
       Uses Resend REST API (port 443 — Railway never blocks this).
    ══════════════════════════════════ */
    private void sendEmail(String to, String subject, String textBody) {
        log.info("Sending email to={} subject={}", to, subject);
        log.info("FROM={} API_KEY_PRESENT={}", appConfig.getMailFrom(),
                resendApiKey != null && !resendApiKey.isBlank());

        /*
         * BUG FIX: Original htmlBody used textBody.replace("\n", "<br>") inside
         * a JSON string that was not HTML-encoded. If textBody contained
         * characters like `"`, `<`, `>`, or `&`, the JSON would be malformed.
         *
         * Fix: escape JSON-special chars in the text field, and use a safe
         * HTML body with proper anchor tag for the activation URL.
         */
        String safeText = textBody
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        String htmlBody = textBody
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");

        String json = String.format("""
                {
                  "from": "%s",
                  "to": ["%s"],
                  "subject": "%s",
                  "text": "%s",
                  "html": "<p>%s</p>"
                }
                """,
                appConfig.getMailFrom(), to, subject, safeText, htmlBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(resendApiKey);

        HttpEntity<String> request = new HttpEntity<>(json, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(RESEND_URL, request, String.class);
            log.info("Email sent — status={} body={}", response.getStatusCode(), response.getBody());
        } catch (HttpClientErrorException e) {
            log.error("Resend rejected — status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Email failed: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Resend call failed — {}", e.getMessage());
            throw new RuntimeException("Email failed: " + e.getMessage());
        }
    }

    /* ══════════════════════════════════
       OTP
    ══════════════════════════════════ */
    public String generateOtp(String email) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        otpStorage.put(email, new OtpData(otp, System.currentTimeMillis()));
        return otp;
    }

    public void sendOtp(String email, String otp) {
        sendEmail(email,
                "Nexus Multiplayer — Verification Code",
                "Your OTP is: " + otp + "\n\nValid for 10 minutes.\n\nDo not share this code.");
        log.info("OTP sent to {}", email);
    }

    public boolean verifyOtp(String email, String inputOtp) {
        OtpData data = otpStorage.get(email);
        if (data == null) return false;

        if (System.currentTimeMillis() - data.getCreatedAt() > OTP_EXPIRY) {
            otpStorage.remove(email);
            log.warn("OTP expired for {}", email);
            return false;
        }

        if (!data.getOtp().equals(inputOtp)) return false;

        otpStorage.remove(email);
        return true;
    }

    public void generateAndSendOtp(String email) {
        String otp = generateOtp(email);
        sendOtp(email, otp);
        log.info("OTP generated and sent for {}", email);
    }

    public void sendActivationLink(String email, String token) {
        log.info("Sending activation link — baseUrl={} from={}", appConfig.getBaseUrl(), appConfig.getMailFrom());

        String url  = appConfig.getBaseUrl() + "/api/users/activate?token=" + token;
        String body = "Welcome to Nexus Multiplayer!\n\n"
                + "Click the link below to activate your account:\n"
                + url + "\n\n"
                + "This link expires in 24 hours.\n\n"
                + "If you did not register, ignore this email.";

        sendEmail(email, "Activate Your Nexus Account", body);
        log.info("Activation email sent to {}", email);
    }

    @Scheduled(fixedRate = 600_000)
    public void cleanExpiredOtps() {
        long now  = System.currentTimeMillis();
        int  was  = otpStorage.size();
        otpStorage.entrySet().removeIf(e -> now - e.getValue().getCreatedAt() > OTP_EXPIRY);
        int removed = was - otpStorage.size();
        if (removed > 0) log.info("Cleaned {} expired OTPs", removed);
    }
}