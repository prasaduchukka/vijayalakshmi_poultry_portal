package com.vpf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeUsernameRequest {
    @NotBlank
    private String newUsername;
    @NotBlank
    private String currentPassword;
}
