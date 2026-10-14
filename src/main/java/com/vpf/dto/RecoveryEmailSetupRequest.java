package com.vpf.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RecoveryEmailSetupRequest {
    @NotBlank private String currentPassword;
    @NotBlank @Email private String recoveryEmail;
}
