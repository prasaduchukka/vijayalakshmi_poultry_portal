package com.vpf.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Sends account-recovery messages through Brevo's HTTPS API.
 * OTP creation, expiry and verification remain in AccountRecoveryController.
 */
@Service
@RequiredArgsConstructor
public class RecoveryEmailService {
    private final RestClient.Builder restClientBuilder;

    @Value("${brevo.api-key:}")
    private String apiKey;

    @Value("${brevo.sender-email:}")
    private String senderEmail;

    @Value("${brevo.sender-name:Vijayalakshmi Poultry Farm}")
    private String senderName;

    public void sendOtp(String to, String otp, String purpose) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Email delivery is not configured. Set BREVO_API_KEY in the hosting environment.");
        }
        if (senderEmail == null || senderEmail.isBlank()) {
            throw new IllegalStateException("Email sender is not configured. Set BREVO_SENDER_EMAIL to a verified sender address in Brevo.");
        }

        String subject = "Vijayalakshmi Poultry Farm account recovery";
        String content = "Your Vijayalakshmi Poultry Farm " + purpose.toLowerCase()
                + " recovery OTP is: " + otp
                + "\n\nThis code expires in 5 minutes. Do not share it with anyone. "
                + "If you did not request this, ignore this email.";

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", new Object[]{Map.of("email", to)},
                "subject", subject,
                "textContent", content
        );

        try {
            restClientBuilder.baseUrl("https://api.brevo.com")
                    .defaultHeader("api-key", apiKey)
                    .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri("/v3/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // Keep secrets and OTPs out of logs/errors returned to the client.
            throw new IllegalStateException("Brevo could not send the recovery email (HTTP "
                    + ex.getStatusCode().value() + "). Check the API key, verified sender, and Brevo account limits.");
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to contact the email delivery service. Please try again later.");
        }
    }
}
