package com.vpf.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ResetPasswordWithTokenRequest {
    @NotBlank @Email private String email;
    @NotBlank private String resetToken;
    @NotBlank @Size(min = 10, max = 72, message = "Password must be 10 to 72 characters") private String newPassword;
}
