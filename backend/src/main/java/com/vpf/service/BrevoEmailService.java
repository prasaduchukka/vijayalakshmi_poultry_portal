package com.vpf.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class BrevoEmailService {
    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.brevo.com/v3")
            .build();
    private final ObjectMapper objectMapper;
    @Value("${app.brevo.api-key:}") private String apiKey;
    @Value("${app.brevo.sender-email:}") private String senderEmail;
    @Value("${app.brevo.sender-name:Vijayalakshmi Poultry Farm}") private String senderName;

    public BrevoEmailService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void sendOtp(String recipient, String otp) {
        if (apiKey == null || apiKey.isBlank() || senderEmail == null || senderEmail.isBlank()) {
            throw new IllegalStateException("Brevo is not configured. Set BREVO_API_KEY and BREVO_SENDER_EMAIL.");
        }
        Map<String, Object> body = Map.of(
            "sender", Map.of("name", senderName, "email", senderEmail),
            "to", new Object[]{Map.of("email", recipient)},
            "subject", "Vijayalakshmi Poultry Farm - Admin recovery OTP",
            "textContent", "Your admin account recovery OTP is " + otp + ". It expires in 5 minutes. If you did not request this, ignore this email."
        );
        restClient.post().uri("/smtp/email")
            .header("api-key", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().toBodilessEntity();
    }
}
