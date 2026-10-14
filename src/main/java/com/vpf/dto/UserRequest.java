package com.vpf.dto;

import com.vpf.entity.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    private String fullName;
    @NotNull
    private UserRole role;
}
